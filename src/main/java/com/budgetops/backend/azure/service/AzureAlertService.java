package com.budgetops.backend.azure.service;

import com.budgetops.backend.aws.dto.AlertCondition;
import com.budgetops.backend.aws.dto.AlertRule;
import com.budgetops.backend.azure.dto.AzureAlert;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Azure 리소스 사용량 임계치 초과 시 알림 발송 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AzureAlertService {
    
    private final AzureAccountRepository accountRepository;
    private final AzureRuleLoader ruleLoader;
    
    /**
     * 모든 Azure 계정의 리소스에 대해 임계치 확인 및 알림 발송
     */
    public List<AzureAlert> checkAllAccounts() {
        List<AzureAccount> allAccounts = accountRepository.findAll();
        log.info("Checking thresholds for {} Azure account(s)", allAccounts.size());
        
        List<AzureAlert> allAlerts = new ArrayList<>();
        
        for (AzureAccount account : allAccounts) {
            try {
                List<AzureAlert> accountAlerts = checkAccount(account.getId());
                allAlerts.addAll(accountAlerts);
            } catch (Exception e) {
                log.error("Failed to check account {}: {}", account.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Total {} Azure alerts generated", allAlerts.size());
        return allAlerts;
    }
    
    /**
     * 특정 Azure 계정의 리소스에 대해 임계치 확인 및 알림 발송
     */
    public List<AzureAlert> checkAccount(Long accountId) {
        AzureAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Azure 계정을 찾을 수 없습니다: " + accountId));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            log.warn("Account {} is not active, skipping alert check", accountId);
            return new ArrayList<>();
        }
        
        log.info("Checking thresholds for account {} ({})", accountId, account.getName());
        
        // TODO: Azure VM 목록 조회 및 메트릭 체크
        // 현재는 빈 리스트 반환
        List<AzureAlert> alerts = new ArrayList<>();
        
        log.info("Generated {} Azure alerts for account {}", alerts.size(), accountId);
        return alerts;
    }
    
    /**
     * 알림 발송
     */
    private void sendAlert(AzureAlert alert) {
        try {
            log.warn("🚨 Azure Alert: {}", alert.getMessage());
            
            alert.setStatus(AzureAlert.AlertStatus.SENT);
            alert.setSentAt(LocalDateTime.now());
            
            // TODO: 실제 알림 발송 로직 (이메일, 슬랙, 웹훅 등)
            
        } catch (Exception e) {
            log.error("Failed to send alert: {}", alert.getMessage(), e);
        }
    }
}

