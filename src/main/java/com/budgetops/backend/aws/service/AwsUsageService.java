package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.constants.FreeTierLimits;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * AWS 사용량 및 프리티어 정보를 수집하는 서비스
 * CloudWatch와 EC2 API를 활용하여 실제 사용량을 추적합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwsUsageService {
    
    private final AwsAccountRepository accountRepository;
    
    /**
     * EC2 인스턴스의 실제 실행 시간 계산 (프리티어 범위 내 사용량 추적)
     * 
     * @param accountId AWS 계정 ID
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 서비스별 사용량 정보
     */
    public List<ServiceUsage> getEc2Usage(Long accountId, Long memberId, String startDate, String endDate) {
        AwsAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }
        
        String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
        
        log.info("Fetching EC2 usage for account {} from {} to {}", accountId, startDate, endDate);
        
        List<ServiceUsage> usageList = new ArrayList<>();
        
        try (Ec2Client ec2Client = createEc2Client(account, region)) {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            
            // 모든 인스턴스 수집
            List<Instance> allInstances = new ArrayList<>();
            for (Reservation reservation : response.reservations()) {
                allInstances.addAll(reservation.instances());
            }
            
            // 프리티어 대상 인스턴스만 필터링
            Map<String, Double> instanceTypeHours = new HashMap<>();
            
            for (Instance instance : allInstances) {
                String instanceType = instance.instanceTypeAsString();
                
                if (FreeTierLimits.isFreeTierInstanceType(instanceType)) {
                    // 실행 시간 계산
                    Instant launchTime = instance.launchTime();
                    Instant now = Instant.now();
                    Instant periodStart = start.atStartOfDay(ZoneId.systemDefault()).toInstant();
                    Instant periodEnd = end.atStartOfDay(ZoneId.systemDefault()).toInstant();
                    
                    // 기간 내 실행 시간 계산
                    Instant actualStart = launchTime.isAfter(periodStart) ? launchTime : periodStart;
                    Instant actualEnd = now.isBefore(periodEnd) ? now : periodEnd;
                    
                    if (actualStart.isBefore(actualEnd) && 
                        (instance.state().name() == InstanceStateName.RUNNING || 
                         instance.state().name() == InstanceStateName.STOPPED)) {
                        
                        // 실행 중인 경우에만 시간 계산
                        if (instance.state().name() == InstanceStateName.RUNNING) {
                            long hours = ChronoUnit.HOURS.between(actualStart, actualEnd);
                            if (hours > 0) {
                                instanceTypeHours.merge(instanceType, (double) hours, (current, add) -> current + add);
                            }
                        }
                    }
                }
            }
            
            // 서비스별 사용량으로 변환
            double totalHours = instanceTypeHours.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            
            if (totalHours > 0) {
                usageList.add(new ServiceUsage(
                    "EC2",
                    totalHours,
                    FreeTierLimits.EC2_FREE_TIER_HOURS_PER_MONTH,
                    totalHours <= FreeTierLimits.EC2_FREE_TIER_HOURS_PER_MONTH,
                    instanceTypeHours
                ));
            }
            
        } catch (Exception e) {
            log.error("Failed to fetch EC2 usage for account {}: {}", accountId, e.getMessage(), e);
            // 에러 발생 시 빈 리스트 반환
        }
        
        return usageList;
    }
    
    /**
     * CloudWatch를 사용하여 실제 사용량 메트릭 수집
     * 
     * @param accountId AWS 계정 ID
     * @param service 서비스명 (EC2, S3, RDS 등)
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 사용량 메트릭
     */
    public UsageMetrics getUsageMetrics(Long accountId, Long memberId, String service, String startDate, String endDate) {
        AwsAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }
        
        String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
        
        try (CloudWatchClient cloudWatchClient = createCloudWatchClient(account, region)) {
            Instant start = LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = LocalDate.parse(endDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
            
            // 서비스별 메트릭 수집
            Map<String, Double> metrics = new HashMap<>();
            
            if ("EC2".equalsIgnoreCase(service)) {
                // EC2 인스턴스 실행 시간 집계
                GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                        .namespace("AWS/EC2")
                        .metricName("CPUUtilization")
                        .startTime(start)
                        .endTime(end)
                        .period(3600) // 1시간 단위
                        .statistics(Statistic.SAMPLE_COUNT)
                        .build();
                
                GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
                
                // 메트릭 데이터 처리
                if (response.datapoints() != null && !response.datapoints().isEmpty()) {
                    double totalSamples = response.datapoints().stream()
                            .mapToDouble(Datapoint::sampleCount)
                            .sum();
                    metrics.put("instanceHours", totalSamples);
                }
            }
            
            return new UsageMetrics(service, metrics);
            
        } catch (Exception e) {
            log.error("Failed to fetch usage metrics for service {}: {}", service, e.getMessage(), e);
            return new UsageMetrics(service, new HashMap<>());
        }
    }
    
    private Ec2Client createEc2Client(AwsAccount account, String region) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                account.getAccessKeyId(),
                account.getSecretKeyEnc()
        );
        
        return Ec2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
    
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
    
    // DTO 클래스들
    public record ServiceUsage(
        String service,
        double usage,
        double freeTierLimit,
        boolean isWithinFreeTier,
        Map<String, Double> details
    ) {}
    
    public record UsageMetrics(
        String service,
        Map<String, Double> metrics
    ) {}
}

