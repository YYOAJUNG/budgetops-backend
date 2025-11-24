package com.budgetops.backend.gcp.service;

import com.budgetops.backend.aws.dto.AlertCondition;
import com.budgetops.backend.aws.dto.AlertRule;
import com.budgetops.backend.gcp.dto.GcpAlert;
import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.dto.GcpResourceResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * GCP 리소스 사용량 임계치 초과 시 알림 발송 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GcpAlertService {
    
    private final GcpAccountRepository accountRepository;
    private final GcpResourceService resourceService;
    private final GcpRuleLoader ruleLoader;
    
    /**
     * 모든 GCP 계정의 리소스에 대해 임계치 확인 및 알림 발송
     */
    public List<GcpAlert> checkAllAccounts() {
        List<GcpAccount> allAccounts = accountRepository.findAll();
        log.info("Checking thresholds for {} GCP account(s)", allAccounts.size());
        
        List<GcpAlert> allAlerts = new ArrayList<>();
        
        for (GcpAccount account : allAccounts) {
            try {
                List<GcpAlert> accountAlerts = checkAccount(account.getId());
                allAlerts.addAll(accountAlerts);
            } catch (Exception e) {
                log.error("Failed to check account {}: {}", account.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Total {} GCP alerts generated", allAlerts.size());
        return allAlerts;
    }
    
    /**
     * 특정 GCP 계정의 리소스에 대해 임계치 확인 및 알림 발송
     */
    public List<GcpAlert> checkAccount(Long accountId) {
        GcpAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("GCP 계정을 찾을 수 없습니다: " + accountId));
        
        log.info("Checking thresholds for account {} ({})", accountId, account.getName());
        
        // GCP 리소스 목록 조회
        List<GcpResourceResponse> resources = new ArrayList<>();
        try {
            // owner ID는 account의 owner에서 가져옴
            if (account.getOwner() != null) {
                GcpResourceListResponse response = resourceService.listResources(accountId, account.getOwner().getId());
                resources = response.getResources();
            }
        } catch (Exception e) {
            log.error("Failed to fetch GCP resources for account {}: {}", accountId, e.getMessage());
            return new ArrayList<>();
        }
        
        // 모든 규칙 로드
        List<AlertRule> rules = ruleLoader.getAllRules();
        
        List<GcpAlert> alerts = new ArrayList<>();
        
        for (GcpResourceResponse resource : resources) {
            // 실행 중인 인스턴스만 체크
            if (!"RUNNING".equalsIgnoreCase(resource.getStatus())) {
                continue;
            }
            
            for (AlertRule rule : rules) {
                List<GcpAlert> ruleAlerts = checkRule(account, resource, rule);
                alerts.addAll(ruleAlerts);
            }
        }
        
        // 알림 발송
        for (GcpAlert alert : alerts) {
            sendAlert(alert);
        }
        
        log.info("Generated {} GCP alerts for account {}", alerts.size(), accountId);
        return alerts;
    }
    
    /**
     * 특정 리소스와 규칙에 대해 임계치 확인
     */
    private List<GcpAlert> checkRule(GcpAccount account, GcpResourceResponse resource, AlertRule rule) {
        List<GcpAlert> alerts = new ArrayList<>();
        
        try {
            // 모든 조건이 만족되는지 확인
            boolean allConditionsMet = true;
            String violatedMetric = null;
            Double currentValue = null;
            Double threshold = null;
            
            for (AlertCondition condition : rule.getConditions()) {
                MetricCheckResult result = checkCondition(account, resource, condition);
                
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
                GcpAlert alert = createAlert(account, resource, rule, violatedMetric, currentValue, threshold);
                alerts.add(alert);
            }
            
        } catch (Exception e) {
            log.error("Failed to check rule {} for resource {}: {}", 
                    rule.getId(), resource.getResourceId(), e.getMessage(), e);
        }
        
        return alerts;
    }
    
    /**
     * 특정 조건에 대해 메트릭 확인
     */
    private MetricCheckResult checkCondition(GcpAccount account, GcpResourceResponse resource, AlertCondition condition) {
        String metric = condition.getMetric();
        
        try {
            // 현재는 리소스 정보에서 메트릭을 추출 (추후 Cloud Monitoring API 연동 가능)
            double averageValue = 0.0;
            boolean hasData = false;
            
            // TODO: Cloud Monitoring API 연동하여 실제 메트릭 조회
            // 현재는 낮은 사용률 시뮬레이션 (알림 발생 가능하도록)
            switch (metric) {
                case "cpu_utilization":
                    // CPU 사용률: 10-45% 사이 랜덤 (40% 임계치 위반 가능)
                    averageValue = 10 + (Math.random() * 35);
                    hasData = true;
                    break;
                    
                case "memory_utilization":
                    // 메모리 사용률: 15-50% 사이 랜덤
                    averageValue = 15 + (Math.random() * 35);
                    hasData = true;
                    break;
                    
                case "network_in":
                    // 네트워크 인바운드: 0.1-5 MB 사이 랜덤
                    averageValue = 0.1 + (Math.random() * 4.9);
                    hasData = true;
                    break;
                    
                default:
                    log.warn("Unknown metric: {}", metric);
                    return MetricCheckResult.notViolated();
            }
            
            log.debug("GCP Metric check - Resource: {}, Metric: {}, Value: {:.2f}", 
                    resource.getResourceName(), metric, averageValue);
            
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
                log.info("GCP Alert triggered - Resource: {}, Metric: {}, Current: {:.2f}, Threshold: {:.2f}", 
                        resource.getResourceName(), metric, averageValue, threshold);
                return MetricCheckResult.violated(averageValue, threshold);
            } else {
                return MetricCheckResult.notViolated();
            }
            
        } catch (Exception e) {
            log.error("Failed to check condition {} for resource {}: {}", 
                    metric, resource.getResourceId(), e.getMessage());
            return MetricCheckResult.notViolated();
        }
    }
    
    /**
     * 알림 생성
     */
    private GcpAlert createAlert(GcpAccount account, GcpResourceResponse resource, 
                                   AlertRule rule, String violatedMetric, 
                                   Double currentValue, Double threshold) {
        
        // 심각도 결정
        GcpAlert.AlertSeverity severity = determineSeverity(violatedMetric, currentValue, threshold);
        
        // 알림 메시지 생성
        String message = String.format(
                "[%s] 리소스 %s(%s)에서 규칙 '%s' 위반 감지.\n" +
                "메트릭: %s, 현재값: %.2f, 임계값: %.2f\n" +
                "%s",
                account.getName(),
                resource.getResourceName(),
                resource.getResourceId(),
                rule.getTitle(),
                violatedMetric,
                currentValue != null ? currentValue : 0.0,
                threshold != null ? threshold : 0.0,
                rule.getRecommendation()
        );
        
        return GcpAlert.builder()
                .accountId(account.getId())
                .accountName(account.getName())
                .resourceId(resource.getResourceId())
                .resourceName(resource.getResourceName())
                .ruleId(rule.getId())
                .ruleTitle(rule.getTitle())
                .violatedMetric(violatedMetric)
                .currentValue(currentValue)
                .threshold(threshold)
                .message(message)
                .severity(severity)
                .status(GcpAlert.AlertStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 심각도 결정
     */
    private GcpAlert.AlertSeverity determineSeverity(String metric, Double currentValue, Double threshold) {
        if (currentValue == null || threshold == null) {
            return GcpAlert.AlertSeverity.WARNING;
        }
        
        // 임계값 대비 현재값 비율
        double ratio = threshold > 0 ? (currentValue / threshold) * 100 : 0;
        
        // 임계값보다 50% 이상 낮으면 CRITICAL (심각한 낭비)
        if (ratio < 50) {
            return GcpAlert.AlertSeverity.CRITICAL;
        } else if (ratio < 70) {
            return GcpAlert.AlertSeverity.WARNING;
        } else {
            return GcpAlert.AlertSeverity.INFO;
        }
    }
    
    /**
     * 알림 발송
     */
    private void sendAlert(GcpAlert alert) {
        try {
            log.warn("🚨 GCP Alert: {}", alert.getMessage());
            
            alert.setStatus(GcpAlert.AlertStatus.SENT);
            alert.setSentAt(LocalDateTime.now());
            
            // TODO: 실제 알림 발송 로직 (이메일, 슬랙, 웹훅 등)
            
        } catch (Exception e) {
            log.error("Failed to send alert: {}", alert.getMessage(), e);
        }
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

