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
    public List<ServiceUsage> getEc2Usage(Long accountId, String startDate, String endDate) {
        AwsAccount account = accountRepository.findById(accountId)
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
            long daysBetween = ChronoUnit.DAYS.between(start, end);
            
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
                                instanceTypeHours.merge(instanceType, (double) hours, Double::sum);
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
     * @param service   서비스명 (EC2, S3, RDS 등)
     * @param startDate 시작 날짜
     * @param endDate   종료 날짜
     * @return 사용량 메트릭
     */
    public UsageMetrics getUsageMetrics(Long accountId, String service, String startDate, String endDate) {
        AwsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }
        
        String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
        String normalizedService = normalizeServiceForMetrics(service);

        try (CloudWatchClient cloudWatchClient = createCloudWatchClient(account, region)) {
            Instant start = LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant end = LocalDate.parse(endDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
            
            // 서비스별 메트릭 수집
            Map<String, Double> metrics = new HashMap<>();

            switch (normalizedService) {
                case "EC2", "EC2_OTHER" -> {
                    // EC2 인스턴스 실행 시간 집계 (기존 로직 유지)
                    GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                            .namespace("AWS/EC2")
                            .metricName("CPUUtilization")
                            .startTime(start)
                            .endTime(end)
                            .period(3600) // 1시간 단위
                            .statistics(Statistic.SAMPLE_COUNT)
                            .build();

                    GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);

                    if (response.datapoints() != null && !response.datapoints().isEmpty()) {
                        double totalSamples = response.datapoints().stream()
                                .mapToDouble(Datapoint::sampleCount)
                                .sum();
                        metrics.put("instanceHours", totalSamples);
                    }
                }
                case "S3" -> {
                    // S3 버킷 기준 사용량 집계 (BucketSizeBytes, NumberOfObjects)
                    // CloudWatch 메트릭을 버킷 단위로 모두 합산
                    double totalBucketSizeBytes = sumMetricAcrossDimensions(
                            cloudWatchClient,
                            "AWS/S3",
                            "BucketSizeBytes",
                            start,
                            end
                    );
                    double totalObjectCount = sumMetricAcrossDimensions(
                            cloudWatchClient,
                            "AWS/S3",
                            "NumberOfObjects",
                            start,
                            end
                    );

                    // Byte → GB 변환
                    double totalBucketSizeGb = totalBucketSizeBytes / (1024.0 * 1024.0 * 1024.0);

                    metrics.put("bucketSizeGb", totalBucketSizeGb);
                    metrics.put("objectCount", totalObjectCount);
                }
                case "RDS" -> {
                    // RDS 인스턴스 실행 시간 집계 (CPUUtilization 샘플 수 기반 근사)
                    GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                            .namespace("AWS/RDS")
                            .metricName("CPUUtilization")
                            .startTime(start)
                            .endTime(end)
                            .period(3600) // 1시간 단위
                            .statistics(Statistic.SAMPLE_COUNT)
                            .build();

                    GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);

                    if (response.datapoints() != null && !response.datapoints().isEmpty()) {
                        double totalSamples = response.datapoints().stream()
                                .mapToDouble(Datapoint::sampleCount)
                                .sum();
                        metrics.put("instanceHours", totalSamples);
                    }
                }
                case "VPC" -> {
                    // VPC는 네트워크 전송량, NAT Gateway 등 여러 리소스로 구성됨
                    // 현재는 별도 메트릭을 집계하지 않고 빈 메트릭만 반환 (확장 포인트)
                    log.info("UsageMetrics requested for VPC - currently no aggregated metrics implemented");
                }
                case "COST_EXPLORER", "OTHERS" -> {
                    // 비용 분석 도구/기타 항목은 CloudWatch 기반 사용량 메트릭이 의미가 없으므로 스킵
                    log.info("UsageMetrics requested for service without CloudWatch usage metrics: {}", service);
                }
                default -> {
                    // 아직 지원하지 않는 서비스는 빈 메트릭 반환
                    log.warn("UsageMetrics requested for unsupported service: {}", service);
                }
            }

            return new UsageMetrics(service, metrics);
            
        } catch (Exception e) {
            log.error("Failed to fetch usage metrics for service {}: {}", service, e.getMessage(), e);
            return new UsageMetrics(service, new HashMap<>());
        }
    }

    /**
     * Cost Explorer 서비스 이름 등을 CloudWatch 메트릭 조회용 키로 정규화
     */
    private String normalizeServiceForMetrics(String service) {
        if (service == null) {
            return "";
        }
        String trimmed = service.trim();
        String upper = trimmed.toUpperCase();

        // Cost Explorer에서 내려오는 대표적인 서비스 이름 매핑
        return switch (upper) {
            case "AMAZON ELASTIC COMPUTE CLOUD - COMPUTE" -> "EC2";
            case "EC2 - OTHER" -> "EC2_OTHER";
            case "AMAZON RELATIONAL DATABASE SERVICE" -> "RDS";
            case "AMAZON SIMPLE STORAGE SERVICE" -> "S3";
            case "AMAZON VIRTUAL PRIVATE CLOUD" -> "VPC";
            case "AWS COST EXPLORER" -> "COST_EXPLORER";
            case "OTHERS" -> "OTHERS";
            default -> upper;
        };
    }

    /**
     * CloudWatch의 ListMetrics + GetMetricStatistics를 사용해
     * 특정 네임스페이스/메트릭을 모든 리소스 차원에 대해 합산
     */
    private double sumMetricAcrossDimensions(CloudWatchClient cloudWatchClient,
                                             String namespace,
                                             String metricName,
                                             Instant start,
                                             Instant end) {
        try {
            ListMetricsRequest listReq = ListMetricsRequest.builder()
                    .namespace(namespace)
                    .metricName(metricName)
                    .build();

            ListMetricsResponse listRes = cloudWatchClient.listMetrics(listReq);
            if (listRes.metrics() == null || listRes.metrics().isEmpty()) {
                return 0.0;
            }

            double total = 0.0;

            for (Metric metric : listRes.metrics()) {
                GetMetricStatisticsRequest statsReq = GetMetricStatisticsRequest.builder()
                        .namespace(namespace)
                        .metricName(metricName)
                        .dimensions(metric.dimensions())
                        .startTime(start)
                        .endTime(end)
                        .period(86400) // 1일 단위
                        .statistics(Statistic.SUM)
                        .build();

                GetMetricStatisticsResponse statsRes = cloudWatchClient.getMetricStatistics(statsReq);
                if (statsRes.datapoints() != null && !statsRes.datapoints().isEmpty()) {
                    total += statsRes.datapoints().stream()
                            .mapToDouble(Datapoint::sum)
                            .sum();
                }
            }

            return total;
        } catch (Exception e) {
            log.warn("Failed to sum metric {} in namespace {}: {}", metricName, namespace, e.getMessage());
            return 0.0;
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

