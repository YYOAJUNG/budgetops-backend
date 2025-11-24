package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import com.budgetops.backend.aws.service.AwsEc2AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AWS EC2 알림 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/aws/alerts")
@RequiredArgsConstructor
public class AwsEc2AlertController {
    
    private final AwsEc2AlertService alertService;
    
    /**
     * 모든 계정의 알림 확인 및 발송
     * POST /api/aws/alerts/check
     */
    @PostMapping("/check")
    public ResponseEntity<List<AwsEc2Alert>> checkAllAccounts() {
        log.info("Manual alert check triggered");
        List<AwsEc2Alert> alerts = alertService.checkAllAccounts();
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * 특정 계정의 알림 확인 및 발송
     * POST /api/aws/alerts/check/{accountId}
     */
    @PostMapping("/check/{accountId}")
    public ResponseEntity<List<AwsEc2Alert>> checkAccount(@PathVariable Long accountId) {
        log.info("Manual alert check triggered for account {}", accountId);
        List<AwsEc2Alert> alerts = alertService.checkAccount(accountId);
        return ResponseEntity.ok(alerts);
    }
}

