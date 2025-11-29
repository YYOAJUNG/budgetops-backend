package com.budgetops.backend.aws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AWS 알림 정보 (EC2, RDS, S3 등 모든 서비스)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwsAlert {
    /**
     * 알림 ID
     */
    private Long id;
    
    /**
     * AWS 계정 ID
     */
    private Long accountId;
    
    /**
     * AWS 계정 이름
     */
    private String accountName;
    
    /**
     * EC2 인스턴스 ID
     */
    private String instanceId;
    
    /**
     * 인스턴스 이름
     */
    private String instanceName;
    
    /**
     * 규칙 ID
     */
    private String ruleId;
    
    /**
     * 규칙 제목
     */
    private String ruleTitle;
    
    /**
     * 위반한 조건 메트릭
     */
    private String violatedMetric;
    
    /**
     * 현재 값
     */
    private Double currentValue;
    
    /**
     * 임계값
     */
    private Double threshold;
    
    /**
     * 알림 메시지
     */
    private String message;
    
    /**
     * 알림 심각도 (INFO, WARNING, CRITICAL)
     */
    private AlertSeverity severity;
    
    /**
     * 알림 상태 (PENDING, SENT, ACKNOWLEDGED)
     */
    private AlertStatus status;
    
    /**
     * 알림 생성 시간
     */
    private LocalDateTime createdAt;
    
    /**
     * 알림 발송 시간
     */
    private LocalDateTime sentAt;
    
    /**
     * 알림 확인 시간
     */
    private LocalDateTime acknowledgedAt;
    
    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
    
    public enum AlertStatus {
        PENDING,      // 대기 중
        SENT,         // 발송됨
        ACKNOWLEDGED  // 확인됨
    }
}

