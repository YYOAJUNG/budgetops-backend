package com.budgetops.backend.ncp.controller;

import com.budgetops.backend.ncp.dto.NcpAlert;
import com.budgetops.backend.ncp.service.NcpAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NCP 알림 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/ncp/alerts")
@RequiredArgsConstructor
public class NcpAlertController {
    
    private final NcpAlertService alertService;
    
    @PostMapping("/check")
    public ResponseEntity<List<NcpAlert>> checkAllAccounts() {
        log.info("Manual NCP alert check triggered");
        List<NcpAlert> alerts = alertService.checkAllAccounts();
        return ResponseEntity.ok(alerts);
    }
    
    @PostMapping("/check/{accountId}")
    public ResponseEntity<List<NcpAlert>> checkAccount(@PathVariable Long accountId) {
        log.info("Manual NCP alert check triggered for account {}", accountId);
        List<NcpAlert> alerts = alertService.checkAccount(accountId);
        return ResponseEntity.ok(alerts);
    }
}

