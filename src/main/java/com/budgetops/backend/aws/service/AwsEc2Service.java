package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

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
                .map(Tag::value)
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
