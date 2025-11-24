package com.budgetops.backend.ncp.service;

import com.budgetops.backend.aws.dto.AlertCondition;
import com.budgetops.backend.aws.dto.AlertRule;
import com.budgetops.backend.ncp.dto.NcpAlert;
import com.budgetops.backend.ncp.dto.NcpServerInstanceResponse;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * NCP 서버 리소스 사용량 임계치 초과 시 알림 발송 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NcpAlertService {
    
    private final NcpAccountRepository accountRepository;
    private final NcpServerService serverService;
    private final NcpRuleLoader ruleLoader;
    
    public List<NcpAlert> checkAllAccounts() {
        List<NcpAccount> allAccounts = accountRepository.findAll();
        log.info("Checking thresholds for {} NCP account(s)", allAccounts.size());
        
        List<NcpAlert> allAlerts = new ArrayList<>();
        
        for (NcpAccount account : allAccounts) {
            try {
                List<NcpAlert> accountAlerts = checkAccount(account.getId());
                allAlerts.addAll(accountAlerts);
            } catch (Exception e) {
                log.error("Failed to check account {}: {}", account.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Total {} NCP alerts generated", allAlerts.size());
        return allAlerts;
    }
    
    public List<NcpAlert> checkAccount(Long accountId) {
        NcpAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("NCP 계정을 찾을 수 없습니다: " + accountId));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            log.warn("Account {} is not active, skipping alert check", accountId);
            return new ArrayList<>();
        }
        
        log.info("Checking thresholds for account {} ({})", accountId, account.getName());
        
        List<NcpServerInstanceResponse> servers;
        try {
            servers = serverService.listInstances(accountId, null);
        } catch (Exception e) {
            log.error("Failed to fetch NCP servers for account {}: {}", accountId, e.getMessage());
            return new ArrayList<>();
        }
        
        List<AlertRule> rules = ruleLoader.getAllRules();
        List<NcpAlert> alerts = new ArrayList<>();
        
        for (NcpServerInstanceResponse server : servers) {
            String status = server.getServerInstanceStatusName() != null ? 
                    server.getServerInstanceStatusName().toUpperCase() : "";
            if (!status.equals("RUN")) {
                continue;
            }
            
            for (AlertRule rule : rules) {
                List<NcpAlert> ruleAlerts = checkRule(account, server, rule);
                alerts.addAll(ruleAlerts);
            }
        }
        
        for (NcpAlert alert : alerts) {
            sendAlert(alert);
        }
        
        log.info("Generated {} NCP alerts for account {}", alerts.size(), accountId);
        return alerts;
    }
    
    private List<NcpAlert> checkRule(NcpAccount account, NcpServerInstanceResponse server, AlertRule rule) {
        List<NcpAlert> alerts = new ArrayList<>();
        
        try {
            boolean allConditionsMet = true;
            String violatedMetric = null;
            Double currentValue = null;
            Double threshold = null;
            
            for (AlertCondition condition : rule.getConditions()) {
                MetricCheckResult result = checkCondition(account, server, condition);
                
                if (!result.isViolated()) {
                    allConditionsMet = false;
                    break;
                }
                
                if (violatedMetric == null) {
                    violatedMetric = condition.getMetric();
                    currentValue = result.getCurrentValue();
                    threshold = result.getThreshold();
                }
            }
            
            if (allConditionsMet) {
                NcpAlert alert = createAlert(account, server, rule, violatedMetric, currentValue, threshold);
                alerts.add(alert);
            }
            
        } catch (Exception e) {
            log.error("Failed to check rule {} for server {}: {}", 
                    rule.getId(), server.getServerInstanceNo(), e.getMessage(), e);
        }
        
        return alerts;
    }
    
    private MetricCheckResult checkCondition(NcpAccount account, NcpServerInstanceResponse server, AlertCondition condition) {
        String metric = condition.getMetric();
        
        try {
            double averageValue = Math.random() * 100; // TODO: 실제 NCP 모니터링 API 연동
            boolean hasData = !metric.equals("network_in");
            
            if (!hasData) {
                return MetricCheckResult.notViolated();
            }
            
            Double threshold = condition.getThresholdAsDouble();
            if (threshold == null) {
                return MetricCheckResult.notViolated();
            }
            
            boolean violated = averageValue < threshold;
            
            return violated ? MetricCheckResult.violated(averageValue, threshold) : MetricCheckResult.notViolated();
            
        } catch (Exception e) {
            log.error("Failed to check condition {} for server {}: {}", metric, server.getServerInstanceNo(), e.getMessage());
            return MetricCheckResult.notViolated();
        }
    }
    
    private NcpAlert createAlert(NcpAccount account, NcpServerInstanceResponse server, 
                                 AlertRule rule, String violatedMetric, 
                                 Double currentValue, Double threshold) {
        
        NcpAlert.AlertSeverity severity = determineSeverity(violatedMetric, currentValue, threshold);
        
        String message = String.format(
                "[%s] 서버 %s(%s)에서 규칙 '%s' 위반 감지.\n" +
                "메트릭: %s, 현재값: %.2f, 임계값: %.2f\n" +
                "%s",
                account.getName(),
                server.getServerName(),
                server.getServerInstanceNo(),
                rule.getTitle(),
                violatedMetric,
                currentValue != null ? currentValue : 0.0,
                threshold != null ? threshold : 0.0,
                rule.getRecommendation()
        );
        
        return NcpAlert.builder()
                .accountId(account.getId())
                .accountName(account.getName())
                .serverInstanceNo(server.getServerInstanceNo())
                .serverName(server.getServerName())
                .ruleId(rule.getId())
                .ruleTitle(rule.getTitle())
                .violatedMetric(violatedMetric)
                .currentValue(currentValue)
                .threshold(threshold)
                .message(message)
                .severity(severity)
                .status(NcpAlert.AlertStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
    
    private NcpAlert.AlertSeverity determineSeverity(String metric, Double currentValue, Double threshold) {
        if (currentValue == null || threshold == null) {
            return NcpAlert.AlertSeverity.WARNING;
        }
        
        double ratio = threshold > 0 ? (currentValue / threshold) * 100 : 0;
        
        if (ratio < 50) {
            return NcpAlert.AlertSeverity.CRITICAL;
        } else if (ratio < 70) {
            return NcpAlert.AlertSeverity.WARNING;
        } else {
            return NcpAlert.AlertSeverity.INFO;
        }
    }
    
    private void sendAlert(NcpAlert alert) {
        try {
            log.warn("🚨 NCP Alert: {}", alert.getMessage());
            alert.setStatus(NcpAlert.AlertStatus.SENT);
            alert.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to send alert: {}", alert.getMessage(), e);
        }
    }
    
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
}

