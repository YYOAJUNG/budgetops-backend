package com.budgetops.backend.azure.service;

import com.budgetops.backend.aws.dto.AlertCondition;
import com.budgetops.backend.aws.dto.AlertRule;
import com.budgetops.backend.azure.dto.AzureAlert;
import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
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
    private final AzureComputeService computeService;
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
        
        // Azure VM 목록 조회
        List<AzureVirtualMachineResponse> vms;
        try {
            vms = computeService.listVirtualMachines(accountId, null);
        } catch (Exception e) {
            log.error("Failed to fetch Azure VMs for account {}: {}", accountId, e.getMessage());
            return new ArrayList<>();
        }
        
        // 모든 규칙 로드
        List<AlertRule> rules = ruleLoader.getAllRules();
        
        List<AzureAlert> alerts = new ArrayList<>();
        
        for (AzureVirtualMachineResponse vm : vms) {
            // 실행 중인 VM만 체크
            String powerState = vm.getPowerState() != null ? vm.getPowerState().toLowerCase() : "";
            if (!powerState.contains("running")) {
                continue;
            }
            
            for (AlertRule rule : rules) {
                List<AzureAlert> ruleAlerts = checkRule(account, vm, rule);
                alerts.addAll(ruleAlerts);
            }
        }
        
        // 알림 발송
        for (AzureAlert alert : alerts) {
            sendAlert(alert);
        }
        
        log.info("Generated {} Azure alerts for account {}", alerts.size(), accountId);
        return alerts;
    }
    
    /**
     * 특정 VM과 규칙에 대해 임계치 확인
     */
    private List<AzureAlert> checkRule(AzureAccount account, AzureVirtualMachineResponse vm, AlertRule rule) {
        List<AzureAlert> alerts = new ArrayList<>();
        
        try {
            // 모든 조건이 만족되는지 확인
            boolean allConditionsMet = true;
            String violatedMetric = null;
            Double currentValue = null;
            Double threshold = null;
            
            for (AlertCondition condition : rule.getConditions()) {
                MetricCheckResult result = checkCondition(account, vm, condition);
                
                if (!result.isViolated()) {
                    allConditionsMet = false;
                    break;
                }
                
                // 위반한 조건 정보 저장 (첫 번째 위반 조건)
                if (violatedMetric == null) {
                    violatedMetric = condition.getMetric();
                    currentValue = result.getCurrentValue();
                    threshold = result.getThreshold();
                }
            }
            
            // 모든 조건이 만족되면 알림 생성
            if (allConditionsMet) {
                AzureAlert alert = createAlert(account, vm, rule, violatedMetric, currentValue, threshold);
                alerts.add(alert);
            }
            
        } catch (Exception e) {
            log.error("Failed to check rule {} for VM {}: {}", 
                    rule.getId(), vm.getId(), e.getMessage(), e);
        }
        
        return alerts;
    }
    
    /**
     * 특정 조건에 대해 메트릭 확인
     */
    private MetricCheckResult checkCondition(AzureAccount account, AzureVirtualMachineResponse vm, AlertCondition condition) {
        String metric = condition.getMetric();
        
        try {
            // TODO: Azure Monitor API 연동하여 실제 메트릭 조회
            // 현재는 임시 mock 데이터로 처리
            double averageValue = 0.0;
            boolean hasData = false;
            
            switch (metric) {
                case "cpu_utilization":
                    // 임시: 랜덤 값 (실제로는 Azure Monitor에서 조회)
                    averageValue = Math.random() * 100;
                    hasData = true;
                    break;
                    
                case "memory_utilization":
                    averageValue = Math.random() * 100;
                    hasData = true;
                    break;
                    
                case "network_in":
                    averageValue = Math.random() * 10;
                    hasData = true;
                    break;
                    
                default:
                    log.warn("Unknown metric: {}", metric);
                    return MetricCheckResult.notViolated();
            }
            
            if (!hasData) {
                return MetricCheckResult.notViolated();
            }
            
            // 임계값과 비교
            Double threshold = condition.getThresholdAsDouble();
            if (threshold == null) {
                return MetricCheckResult.notViolated();
            }
            
            // 기본 연산자는 < (미만), 즉 현재값이 임계값보다 작으면 위반
            boolean violated = averageValue < threshold;
            
            if (violated) {
                return MetricCheckResult.violated(averageValue, threshold);
            } else {
                return MetricCheckResult.notViolated();
            }
            
        } catch (Exception e) {
            log.error("Failed to check condition {} for VM {}: {}", 
                    metric, vm.getId(), e.getMessage());
            return MetricCheckResult.notViolated();
        }
    }
    
    /**
     * 알림 생성
     */
    private AzureAlert createAlert(AzureAccount account, AzureVirtualMachineResponse vm, 
                                   AlertRule rule, String violatedMetric, 
                                   Double currentValue, Double threshold) {
        
        // 심각도 결정
        AzureAlert.AlertSeverity severity = determineSeverity(violatedMetric, currentValue, threshold);
        
        // 알림 메시지 생성
        String message = String.format(
                "[%s] VM %s(%s)에서 규칙 '%s' 위반 감지.\n" +
                "메트릭: %s, 현재값: %.2f, 임계값: %.2f\n" +
                "%s",
                account.getName(),
                vm.getName(),
                vm.getId(),
                rule.getTitle(),
                violatedMetric,
                currentValue != null ? currentValue : 0.0,
                threshold != null ? threshold : 0.0,
                rule.getRecommendation()
        );
        
        return AzureAlert.builder()
                .accountId(account.getId())
                .accountName(account.getName())
                .resourceId(vm.getId())
                .resourceName(vm.getName())
                .ruleId(rule.getId())
                .ruleTitle(rule.getTitle())
                .violatedMetric(violatedMetric)
                .currentValue(currentValue)
                .threshold(threshold)
                .message(message)
                .severity(severity)
                .status(AzureAlert.AlertStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 심각도 결정
     */
    private AzureAlert.AlertSeverity determineSeverity(String metric, Double currentValue, Double threshold) {
        if (currentValue == null || threshold == null) {
            return AzureAlert.AlertSeverity.WARNING;
        }
        
        // 임계값 대비 현재값 비율
        double ratio = threshold > 0 ? (currentValue / threshold) * 100 : 0;
        
        // 임계값보다 50% 이상 낮으면 CRITICAL (심각한 낭비)
        if (ratio < 50) {
            return AzureAlert.AlertSeverity.CRITICAL;
        } else if (ratio < 70) {
            return AzureAlert.AlertSeverity.WARNING;
        } else {
            return AzureAlert.AlertSeverity.INFO;
        }
    }
    
    /**
     * 메트릭 확인 결과
     */
    private static class MetricCheckResult {
        private final boolean violated;
        private final Double currentValue;
        private final Double threshold;
        
        private MetricCheckResult(boolean violated, Double currentValue, Double threshold) {
            this.violated = violated;
            this.currentValue = currentValue;
            this.threshold = threshold;
        }
        
        public static MetricCheckResult violated(double currentValue, double threshold) {
            return new MetricCheckResult(true, currentValue, threshold);
        }
        
        public static MetricCheckResult notViolated() {
            return new MetricCheckResult(false, null, null);
        }
        
        public boolean isViolated() {
            return violated;
        }
        
        public Double getCurrentValue() {
            return currentValue;
        }
        
        public Double getThreshold() {
            return threshold;
        }
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

