package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.gcp.dto.GcpAlert;
import com.budgetops.backend.gcp.service.GcpAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GCP 알림 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/gcp/alerts")
@RequiredArgsConstructor
public class GcpAlertController {
    
    private final GcpAlertService alertService;
    
    /**
     * 모든 GCP 계정의 알림 확인 및 발송
     * POST /api/gcp/alerts/check
     */
    @PostMapping("/check")
    public ResponseEntity<List<GcpAlert>> checkAllAccounts() {
        log.info("Manual GCP alert check triggered");
        List<GcpAlert> alerts = alertService.checkAllAccounts();
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * 특정 GCP 계정의 알림 확인 및 발송
     * POST /api/gcp/alerts/check/{accountId}
     */
    @PostMapping("/check/{accountId}")
    public ResponseEntity<List<GcpAlert>> checkAccount(@PathVariable Long accountId) {
        log.info("Manual GCP alert check triggered for account {}", accountId);
        List<GcpAlert> alerts = alertService.checkAccount(accountId);
        return ResponseEntity.ok(alerts);
    }
}

