package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import com.budgetops.backend.aws.service.AwsAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AWS 통합 알림 API 컨트롤러
 * EC2, RDS, S3 등 모든 AWS 서비스의 알림 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/aws/alerts")
@RequiredArgsConstructor
public class AwsEc2AlertController {
    
    private final AwsAlertService alertService;
    
    /**
     * 모든 계정의 모든 AWS 서비스 알림 확인 및 발송
     * POST /api/aws/alerts/check
     */
    @PostMapping("/check")
    public ResponseEntity<List<AwsEc2Alert>> checkAllAccounts() {
        log.info("Manual alert check triggered for all AWS services");
        List<AwsEc2Alert> alerts = alertService.checkAllServices();
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * 특정 계정의 모든 AWS 서비스 알림 확인 및 발송
     * POST /api/aws/alerts/check/{accountId}
     */
    @PostMapping("/check/{accountId}")
    public ResponseEntity<List<AwsEc2Alert>> checkAccount(@PathVariable Long accountId) {
        log.info("Manual alert check triggered for all AWS services for account {}", accountId);
        List<AwsEc2Alert> alerts = alertService.checkAllServicesForAccount(accountId);
        return ResponseEntity.ok(alerts);
    }
}

