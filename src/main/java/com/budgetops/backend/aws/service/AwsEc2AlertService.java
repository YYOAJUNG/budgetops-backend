package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.*;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.billing.entity.Member;
import com.budgetops.backend.billing.repository.MemberRepository;
import com.budgetops.backend.notification.service.SlackNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * AWS EC2 리소스 사용량 임계치 초과 시 알림 발송 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwsEc2AlertService {
    
    private final AwsAccountRepository accountRepository;
    private final AwsEc2Service ec2Service;
    private final AwsEc2RuleLoader ruleLoader;
    private final MemberRepository memberRepository;
    private final SlackNotificationService slackNotificationService;
    
    /**
     * 모든 활성 AWS 계정의 EC2 인스턴스에 대해 임계치 확인 및 알림 발송
     */
    @Transactional(readOnly = true)
    public List<AwsEc2Alert> checkAllAccounts() {
        List<AwsAccount> activeAccounts = accountRepository.findByActiveTrue();
        log.info("Checking thresholds for {} active AWS account(s)", activeAccounts.size());
        
        List<AwsEc2Alert> allAlerts = new ArrayList<>();
        
        for (AwsAccount account : activeAccounts) {
            try {
                List<AwsEc2Alert> accountAlerts = checkAccount(account.getId());
                allAlerts.addAll(accountAlerts);
            } catch (Exception e) {
                log.error("Failed to check account {}: {}", account.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Total {} alerts generated", allAlerts.size());
        return allAlerts;
    }
    
    /**
     * 특정 AWS 계정의 EC2 인스턴스에 대해 임계치 확인 및 알림 발송
     */
    @Transactional(readOnly = true)
    public List<AwsEc2Alert> checkAccount(Long accountId) {
        AwsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            log.warn("Account {} is not active, skipping alert check", accountId);
            return new ArrayList<>();
        }
        
        log.info("Checking thresholds for account {} ({})", accountId, account.getName());
        
        // EC2 인스턴스 목록 조회
        List<AwsEc2InstanceResponse> instances = ec2Service.listInstances(accountId, null);
        
        // 모든 규칙 로드
        List<AlertRule> rules = ruleLoader.getAllRules();
        
        List<AwsEc2Alert> alerts = new ArrayList<>();
        
        for (AwsEc2InstanceResponse instance : instances) {
            // 실행 중인 인스턴스만 체크
            if (!"running".equalsIgnoreCase(instance.getState())) {
                continue;
            }
            
            for (AlertRule rule : rules) {
                List<AwsEc2Alert> ruleAlerts = checkRule(account, instance, rule);
                alerts.addAll(ruleAlerts);
            }
        }
        
        // 알림 발송
        for (AwsEc2Alert alert : alerts) {
            sendAlert(alert);
        }
        
        log.info("Generated {} alerts for account {}", alerts.size(), accountId);
        return alerts;
    }
    
    /**
     * 특정 인스턴스와 규칙에 대해 임계치 확인
     */
    private List<AwsEc2Alert> checkRule(AwsAccount account, AwsEc2InstanceResponse instance, AlertRule rule) {
        List<AwsEc2Alert> alerts = new ArrayList<>();
        
        try {
            // 모든 조건이 만족되는지 확인
            boolean allConditionsMet = true;
            String violatedMetric = null;
            Double currentValue = null;
            Double threshold = null;
            
            for (AlertCondition condition : rule.getConditions()) {
                MetricCheckResult result = checkCondition(account, instance, condition);
                
                if (!result.isViolated()) {
                    allConditionsMet = false;
                    break;
                }
                
                // 위반한 조건 정보 저장 (첫 번째 위반 조건)
                if (violatedMetric == null) {
                    violatedMetric = condition.getMetric();
                    currentValue = result.getCurrentValue();
                    threshold = result.getThreshold();
                }
            }
            
            // 모든 조건이 만족되면 알림 생성
            if (allConditionsMet) {
                AwsEc2Alert alert = createAlert(account, instance, rule, violatedMetric, currentValue, threshold);
                alerts.add(alert);
            }
            
        } catch (Exception e) {
            log.error("Failed to check rule {} for instance {}: {}", 
                    rule.getId(), instance.getInstanceId(), e.getMessage(), e);
        }
        
        return alerts;
    }
    
    /**
     * 특정 조건에 대해 메트릭 확인
     */
    private MetricCheckResult checkCondition(AwsAccount account, AwsEc2InstanceResponse instance, AlertCondition condition) {
        String metric = condition.getMetric();
        String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
        int periodDays = condition.getPeriodInDays();
        
        try {
            // period에 해당하는 시간만큼 메트릭 조회
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(periodDays, ChronoUnit.DAYS);
            
            try (CloudWatchClient cloudWatchClient = createCloudWatchClient(account, region)) {
                // 메트릭별로 처리
                double averageValue = 0.0;
                boolean hasData = false;
                
                switch (metric) {
                    case "cpu_utilization":
                        averageValue = getAverageMetricValue(cloudWatchClient, instance.getInstanceId(), 
                                "AWS/EC2", "CPUUtilization", "Percent", startTime, endTime);
                        hasData = true;
                        break;
                        
                    case "memory_utilization":
                        // CloudWatch Agent가 설치된 경우에만 사용 가능
                        averageValue = getAverageMetricValue(cloudWatchClient, instance.getInstanceId(), 
                                "CWAgent", "mem_used_percent", "Percent", startTime, endTime);
                        hasData = averageValue > 0; // 데이터가 없으면 0
                        break;
                        
                    case "network_in":
                        averageValue = getAverageMetricValue(cloudWatchClient, instance.getInstanceId(), 
                                "AWS/EC2", "NetworkIn", "Bytes", startTime, endTime);
                        hasData = true;
                        // 바이트를 MB로 변환 (기간 내 평균)
                        averageValue = averageValue / (1024.0 * 1024.0);
                        break;
                        
                    case "network_out":
                        averageValue = getAverageMetricValue(cloudWatchClient, instance.getInstanceId(), 
                                "AWS/EC2", "NetworkOut", "Bytes", startTime, endTime);
                        hasData = true;
                        // 바이트를 MB로 변환 (기간 내 평균)
                        averageValue = averageValue / (1024.0 * 1024.0);
                        break;
                        
                    default:
                        log.warn("Unknown metric: {}", metric);
                        return MetricCheckResult.notViolated();
                }
                
                if (!hasData) {
                    return MetricCheckResult.notViolated();
                }
                
                // 임계값과 비교
                Double threshold = condition.getThresholdAsDouble();
                if (threshold == null) {
                    return MetricCheckResult.notViolated();
                }
                
                // 기본 연산자는 < (미만), 즉 현재값이 임계값보다 작으면 위반
                boolean violated = averageValue < threshold;
                
                if (violated) {
                    return MetricCheckResult.violated(averageValue, threshold);
                } else {
                    return MetricCheckResult.notViolated();
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check condition {} for instance {}: {}", 
                    metric, instance.getInstanceId(), e.getMessage());
            return MetricCheckResult.notViolated();
        }
    }
    
    /**
     * CloudWatch에서 메트릭의 평균값 조회
     */
    private double getAverageMetricValue(CloudWatchClient cloudWatchClient, String instanceId, 
                                        String namespace, String metricName, String unit,
                                        Instant startTime, Instant endTime) {
        try {
            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                    .namespace(namespace)
                    .metricName(metricName)
                    .dimensions(Dimension.builder()
                            .name("InstanceId")
                            .value(instanceId)
                            .build())
                    .startTime(startTime)
                    .endTime(endTime)
                    .period(3600) // 1시간 단위
                    .statistics(Statistic.AVERAGE)
                    .build();
            
            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            
            if (response.datapoints().isEmpty()) {
                return 0.0;
            }
            
            // 모든 데이터포인트의 평균값 계산
            double sum = response.datapoints().stream()
                    .mapToDouble(Datapoint::average)
                    .sum();
            
            return sum / response.datapoints().size();
            
        } catch (CloudWatchException e) {
            log.warn("Failed to get metric {} for instance {}: {}", 
                    metricName, instanceId, e.awsErrorDetails().errorMessage());
            return 0.0;
        }
    }
    
    /**
     * 알림 생성
     */
    private AwsEc2Alert createAlert(AwsAccount account, AwsEc2InstanceResponse instance, 
                                   AlertRule rule, String violatedMetric, 
                                   Double currentValue, Double threshold) {
        
        // 심각도 결정
        AwsEc2Alert.AlertSeverity severity = determineSeverity(violatedMetric, currentValue, threshold);
        
        // 알림 메시지 생성
        String message = String.format(
                "[%s] 인스턴스 %s(%s)에서 규칙 '%s' 위반 감지.\n" +
                "메트릭: %s, 현재값: %.2f, 임계값: %.2f\n" +
                "%s",
                account.getName(),
                instance.getName(),
                instance.getInstanceId(),
                rule.getTitle(),
                violatedMetric,
                currentValue != null ? currentValue : 0.0,
                threshold != null ? threshold : 0.0,
                rule.getRecommendation()
        );
        
        return AwsEc2Alert.builder()
                .accountId(account.getId())
                .accountName(account.getName())
                .instanceId(instance.getInstanceId())
                .instanceName(instance.getName())
                .ruleId(rule.getId())
                .ruleTitle(rule.getTitle())
                .violatedMetric(violatedMetric)
                .currentValue(currentValue)
                .threshold(threshold)
                .message(message)
                .severity(severity)
                .status(AwsEc2Alert.AlertStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 심각도 결정
     */
    private AwsEc2Alert.AlertSeverity determineSeverity(String metric, Double currentValue, Double threshold) {
        if (currentValue == null || threshold == null) {
            return AwsEc2Alert.AlertSeverity.WARNING;
        }
        
        // 임계값 대비 현재값 비율
        double ratio = threshold > 0 ? (currentValue / threshold) * 100 : 0;
        
        // 임계값보다 50% 이상 낮으면 CRITICAL (심각한 낭비)
        if (ratio < 50) {
            return AwsEc2Alert.AlertSeverity.CRITICAL;
        } else if (ratio < 70) {
            return AwsEc2Alert.AlertSeverity.WARNING;
        } else {
            return AwsEc2Alert.AlertSeverity.INFO;
        }
    }
    
    /**
     * 알림 발송
     */
    private void sendAlert(AwsEc2Alert alert) {
        try {
            // 현재는 로그로만 발송 (나중에 이메일, 웹훅 등으로 확장 가능)
            log.warn("🚨 AWS EC2 Alert: {}", alert.getMessage());
            
            // 알림 상태 업데이트
            alert.setStatus(AwsEc2Alert.AlertStatus.SENT);
            alert.setSentAt(LocalDateTime.now());

            notifySlackSubscribers(alert);
            
        } catch (Exception e) {
            log.error("Failed to send alert: {}", alert.getMessage(), e);
        }
    }

    private void notifySlackSubscribers(AwsEc2Alert alert) {
        List<Member> subscribers = memberRepository.findBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
        if (subscribers.isEmpty()) {
            return;
        }

        for (Member member : subscribers) {
            if (!StringUtils.hasText(member.getSlackWebhookUrl())) {
                continue;
            }
            slackNotificationService.sendEc2Alert(member.getSlackWebhookUrl(), alert);
        }
    }
    
    /**
     * CloudWatch 클라이언트 생성
     */
    private CloudWatchClient createCloudWatchClient(AwsAccount account, String region) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                account.getAccessKeyId(),
                account.getSecretKeyEnc()
        );
        
        return CloudWatchClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
    
    /**
     * 메트릭 확인 결과
     */
    private static class MetricCheckResult {
        private final boolean violated;
        private final Double currentValue;
        private final Double threshold;
        
        private MetricCheckResult(boolean violated, Double currentValue, Double threshold) {
            this.violated = violated;
            this.currentValue = currentValue;
            this.threshold = threshold;
        }
        
        public static MetricCheckResult violated(double currentValue, double threshold) {
            return new MetricCheckResult(true, currentValue, threshold);
        }
        
        public static MetricCheckResult notViolated() {
            return new MetricCheckResult(false, null, null);
        }
        
        public boolean isViolated() {
            return violated;
        }
        
        public Double getCurrentValue() {
            return currentValue;
        }
        
        public Double getThreshold() {
            return threshold;
        }
    }
}

