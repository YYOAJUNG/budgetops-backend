package com.budgetops.backend.azure.controller;

import com.budgetops.backend.azure.dto.AzureAlert;
import com.budgetops.backend.azure.service.AzureAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Azure 알림 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/azure/alerts")
@RequiredArgsConstructor
public class AzureAlertController {
    
    private final AzureAlertService alertService;
    
    /**
     * 모든 Azure 계정의 알림 확인 및 발송
     * POST /api/azure/alerts/check
     */
    @PostMapping("/check")
    public ResponseEntity<List<AzureAlert>> checkAllAccounts() {
        log.info("Manual Azure alert check triggered");
        List<AzureAlert> alerts = alertService.checkAllAccounts();
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * 특정 Azure 계정의 알림 확인 및 발송
     * POST /api/azure/alerts/check/{accountId}
     */
    @PostMapping("/check/{accountId}")
    public ResponseEntity<List<AzureAlert>> checkAccount(@PathVariable Long accountId) {
        log.info("Manual Azure alert check triggered for account {}", accountId);
        List<AzureAlert> alerts = alertService.checkAccount(accountId);
        return ResponseEntity.ok(alerts);
    }
}

