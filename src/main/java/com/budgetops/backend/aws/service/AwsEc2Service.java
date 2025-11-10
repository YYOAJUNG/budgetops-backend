package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.dto.AwsEc2MetricsResponse;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsEc2Service {
    
    private final AwsAccountRepository accountRepository;
    
    /**
     * 특정 AWS 계정의 EC2 인스턴스 목록 조회
     * 
     * @param accountId AWS 계정 ID
     * @param region 조회할 리전 (null이면 계정의 기본 리전)
     * @return EC2 인스턴스 목록
     */
    public List<AwsEc2InstanceResponse> listInstances(Long accountId, String region) {
        AwsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }
        
        String targetRegion = region != null ? region : account.getDefaultRegion();
        if (targetRegion == null) {
            targetRegion = "us-east-1"; // fallback
        }
        
        log.info("Fetching EC2 instances for account {} in region {}", accountId, targetRegion);
        
        try (Ec2Client ec2Client = createEc2Client(account, targetRegion)) {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            
            List<AwsEc2InstanceResponse> instances = new ArrayList<>();
            
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    instances.add(convertToResponse(instance));
                }
            }
            
            log.info("Found {} EC2 instances", instances.size());
            return instances;
            
        } catch (Ec2Exception e) {
            log.error("Failed to fetch EC2 instances: {}", e.awsErrorDetails().errorMessage());
            throw new RuntimeException("EC2 인스턴스 조회 실패: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("Unexpected error while fetching EC2 instances", e);
            throw new RuntimeException("EC2 인스턴스 조회 중 오류 발생: " + e.getMessage());
        }
    }
    
    /**
     * 특정 EC2 인스턴스 상세 조회
     */
    public AwsEc2InstanceResponse getEc2Instance(Long accountId, String instanceId, String region) {
        AwsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        String targetRegion = region != null ? region : account.getDefaultRegion();
        if (targetRegion == null) {
            targetRegion = "us-east-1";
        }
        
        try (Ec2Client ec2Client = createEc2Client(account, targetRegion)) {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();
            
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            
            if (response.reservations().isEmpty() || 
                response.reservations().get(0).instances().isEmpty()) {
                throw new ResponseStatusException(NOT_FOUND, "EC2 인스턴스를 찾을 수 없습니다.");
            }
            
            Instance instance = response.reservations().get(0).instances().get(0);
            return convertToResponse(instance);
            
        } catch (Ec2Exception e) {
            log.error("Failed to fetch EC2 instance {}: {}", instanceId, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("EC2 인스턴스 조회 실패: " + e.awsErrorDetails().errorMessage());
        }
    }
    
    /**
     * EC2 인스턴스의 CloudWatch 메트릭 조회
     * 
     * @param accountId AWS 계정 ID
     * @param instanceId EC2 인스턴스 ID
     * @param region 리전 (null이면 계정의 기본 리전)
     * @param hours 조회할 시간 범위 (기본값: 1시간)
     * @return 메트릭 데이터 (CPU, NetworkIn, NetworkOut, MemoryUtilization)
     */
    public AwsEc2MetricsResponse getInstanceMetrics(Long accountId, String instanceId, String region, Integer hours) {
        AwsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }
        
        String targetRegion = region != null ? region : account.getDefaultRegion();
        if (targetRegion == null) {
            targetRegion = "us-east-1";
        }
        
        int hoursToQuery = hours != null && hours > 0 ? hours : 1;
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(hoursToQuery, ChronoUnit.HOURS);
        
        log.info("Fetching metrics for EC2 instance {} in region {} for the last {} hours", 
                instanceId, targetRegion, hoursToQuery);
        
        try (CloudWatchClient cloudWatchClient = createCloudWatchClient(account, targetRegion)) {
            // CPU Utilization
            List<AwsEc2MetricsResponse.MetricDataPoint> cpuMetrics = getMetricStatistics(
                    cloudWatchClient, instanceId, "CPUUtilization", "Percent", startTime, endTime);
            
            // Network In
            List<AwsEc2MetricsResponse.MetricDataPoint> networkInMetrics = getMetricStatistics(
                    cloudWatchClient, instanceId, "NetworkIn", "Bytes", startTime, endTime);
            
            // Network Out
            List<AwsEc2MetricsResponse.MetricDataPoint> networkOutMetrics = getMetricStatistics(
                    cloudWatchClient, instanceId, "NetworkOut", "Bytes", startTime, endTime);
            
            // Memory Utilization (CloudWatch Agent가 설치된 경우에만 사용 가능)
            List<AwsEc2MetricsResponse.MetricDataPoint> memoryMetrics = getMetricStatisticsWithNamespace(
                    cloudWatchClient, instanceId, "CWAgent", "mem_used_percent", "Percent", startTime, endTime);
            
            return AwsEc2MetricsResponse.builder()
                    .instanceId(instanceId)
                    .region(targetRegion)
                    .cpuUtilization(cpuMetrics)
                    .networkIn(networkInMetrics)
                    .networkOut(networkOutMetrics)
                    .memoryUtilization(memoryMetrics)
                    .build();
            
        } catch (CloudWatchException e) {
            log.error("Failed to fetch CloudWatch metrics for instance {}: {}", 
                    instanceId, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("메트릭 조회 실패: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("Unexpected error while fetching metrics for instance {}", instanceId, e);
            throw new RuntimeException("메트릭 조회 중 오류 발생: " + e.getMessage());
        }
    }
    
    /**
     * CloudWatch에서 특정 메트릭의 통계 데이터 조회 (AWS/EC2 네임스페이스)
     */
    private List<AwsEc2MetricsResponse.MetricDataPoint> getMetricStatistics(
            CloudWatchClient cloudWatchClient,
            String instanceId,
            String metricName,
            String unit,
            Instant startTime,
            Instant endTime) {
        return getMetricStatisticsWithNamespace(
                cloudWatchClient, instanceId, "AWS/EC2", metricName, unit, startTime, endTime);
    }
    
    /**
     * CloudWatch에서 특정 메트릭의 통계 데이터 조회 (네임스페이스 지정 가능)
     */
    private List<AwsEc2MetricsResponse.MetricDataPoint> getMetricStatisticsWithNamespace(
            CloudWatchClient cloudWatchClient,
            String instanceId,
            String namespace,
            String metricName,
            String unit,
            Instant startTime,
            Instant endTime) {
        
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
                    .period(300) // 5분 간격
                    .statistics(Statistic.AVERAGE)
                    .build();
            
            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            
            return response.datapoints().stream()
                    .map(datapoint -> AwsEc2MetricsResponse.MetricDataPoint.builder()
                            .timestamp(datapoint.timestamp().toString())
                            .value(datapoint.average())
                            .unit(unit)
                            .build())
                    .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                    .collect(Collectors.toList());
                    
        } catch (CloudWatchException e) {
            // 메트릭이 없는 경우 (예: MemoryUtilization은 CloudWatch Agent 필요)
            log.warn("Metric {} in namespace {} not available for instance {}: {}", 
                    metricName, namespace, instanceId, e.awsErrorDetails().errorMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * CloudWatch 클라이언트 생성
     */
    private CloudWatchClient createCloudWatchClient(AwsAccount account, String region) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                account.getAccessKeyId(),
                account.getSecretKeyEnc() // 암호화된 값이 자동으로 복호화됨
        );
        
        return CloudWatchClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
    
    /**
     * EC2 클라이언트 생성
     */
    private Ec2Client createEc2Client(AwsAccount account, String region) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                account.getAccessKeyId(),
                account.getSecretKeyEnc() // 암호화된 값이 자동으로 복호화됨
        );
        
        return Ec2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
    
    /**
     * EC2 Instance를 Response DTO로 변환
     */
    private AwsEc2InstanceResponse convertToResponse(Instance instance) {
        // Name 태그 찾기
        String name = instance.tags().stream()
                .filter(tag -> "Name".equals(tag.key()))
                .map(software.amazon.awssdk.services.ec2.model.Tag::value)
                .findFirst()
                .orElse("");
        
        // 시작 시간 포맷팅
        String launchTime = "";
        if (instance.launchTime() != null) {
            launchTime = instance.launchTime().toString();
        }
        
        // Instance Type 값 변환
        String instanceType = "";
        if (instance.instanceType() != null) {
            instanceType = instance.instanceTypeAsString();
        }
        
        return AwsEc2InstanceResponse.builder()
                .instanceId(instance.instanceId())
                .name(name)
                .instanceType(instanceType)
                .state(instance.state() != null ? instance.state().nameAsString() : "")
                .availabilityZone(instance.placement() != null ? instance.placement().availabilityZone() : "")
                .publicIp(instance.publicIpAddress() != null ? instance.publicIpAddress() : "")
                .privateIp(instance.privateIpAddress() != null ? instance.privateIpAddress() : "")
                .launchTime(launchTime)
                .build();
    }
}
