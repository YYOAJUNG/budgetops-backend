package com.budgetops.backend.ncp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * NCP 서버 알림 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NcpAlert {
    private Long id;
    private Long accountId;
    private String accountName;
    private String serverInstanceNo;
    private String serverName;
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

