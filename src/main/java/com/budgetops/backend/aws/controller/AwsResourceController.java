package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsResource;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.aws.service.AwsResourceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aws")
@RequiredArgsConstructor
public class AwsResourceController {

    private final AwsResourceQueryService service;
    private final AwsEc2Service ec2Service;

    // 특정 계정의 모든 리소스
    @GetMapping("/accounts/{accountId}/resources")
    public ResponseEntity<List<AwsResource>> byAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.findByAccount(accountId));
    }

    // 리소스 타입별 조회
    @GetMapping("/resources")
    public ResponseEntity<List<AwsResource>> byType(@RequestParam String resourceType) {
        return ResponseEntity.ok(service.findByType(resourceType));
    }

    // 계정 + 타입 조회
    @GetMapping("/accounts/{accountId}/resources/{resourceType}")
    public ResponseEntity<List<AwsResource>> byAccountAndType(@PathVariable Long accountId, @PathVariable String resourceType) {
        return ResponseEntity.ok(service.findByAccountAndType(accountId, resourceType));
    }

    // ===== EC2 인스턴스 조회 =====
    
    /**
     * 특정 계정의 EC2 인스턴스 목록 조회
     * 
     * @param accountId AWS 계정 ID
     * @param region 조회할 리전 (선택사항, 없으면 계정 기본 리전 사용)
     * @return EC2 인스턴스 목록
     */
    @GetMapping("/accounts/{accountId}/ec2/instances")
    public ResponseEntity<List<AwsEc2InstanceResponse>> listEc2Instances(
            @PathVariable Long accountId,
            @RequestParam(required = false) String region) {
        List<AwsEc2InstanceResponse> instances = ec2Service.listEc2Instances(accountId, region);
        return ResponseEntity.ok(instances);
    }

    /**
     * 특정 EC2 인스턴스 상세 조회
     * 
     * @param accountId AWS 계정 ID
     * @param instanceId EC2 인스턴스 ID
     * @param region 리전 (선택사항)
     * @return EC2 인스턴스 상세 정보
     */
    @GetMapping("/accounts/{accountId}/ec2/instances/{instanceId}")
    public ResponseEntity<AwsEc2InstanceResponse> getEc2Instance(
            @PathVariable Long accountId,
            @PathVariable String instanceId,
            @RequestParam(required = false) String region) {
        AwsEc2InstanceResponse instance = ec2Service.getEc2Instance(accountId, instanceId, region);
        return ResponseEntity.ok(instance);
    }
}


