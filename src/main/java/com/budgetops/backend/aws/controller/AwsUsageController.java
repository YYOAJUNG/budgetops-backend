package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.service.AwsUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AWS 사용량 메트릭 조회용 API
 * - CloudWatch 기반 EC2 / S3 / RDS 등의 사용량 집계를 노출
 */
@Slf4j
@RestController
@RequestMapping("/api/aws/accounts")
@RequiredArgsConstructor
public class AwsUsageController {

    private final AwsUsageService awsUsageService;

    /**
     * 특정 AWS 계정의 서비스별 사용량 메트릭 조회
     *
     * 예:
     * GET /api/aws/accounts/{accountId}/usage?service=Amazon%20Elastic%20Compute%20Cloud%20-%20Compute&startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/{accountId}/usage")
    public ResponseEntity<AwsUsageService.UsageMetrics> getUsage(
            @PathVariable Long accountId,
            @RequestParam String service,
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        log.info("Fetching usage metrics: accountId={}, service={}, startDate={}, endDate={}",
                accountId, service, startDate, endDate);

        AwsUsageService.UsageMetrics metrics = awsUsageService.getUsageMetrics(accountId, service, startDate, endDate);
        return ResponseEntity.ok(metrics);
    }
}


