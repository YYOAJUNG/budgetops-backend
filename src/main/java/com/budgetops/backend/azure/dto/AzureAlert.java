package com.budgetops.backend.azure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Azure 리소스 알림 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AzureAlert {
    private Long id;
    private Long accountId;
    private String accountName;
    private String resourceId;
    private String resourceName;
    private String ruleId;
    private String ruleTitle;
    private String violatedMetric;
    private Double currentValue;
    private Double threshold;
    private String message;
    private AlertSeverity severity;
    private AlertStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private LocalDateTime acknowledgedAt;
    
    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
    
    public enum AlertStatus {
        PENDING,
        SENT,
        ACKNOWLEDGED
    }
}

