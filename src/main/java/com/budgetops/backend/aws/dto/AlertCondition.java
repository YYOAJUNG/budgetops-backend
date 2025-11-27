package com.budgetops.backend.aws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 알림 조건 정보
 * yaml 파일의 conditions를 파싱한 결과
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertCondition {
    /**
     * 메트릭 이름 (cpu_utilization, memory_utilization, network_in 등)
     */
    private String metric;
    
    /**
     * 임계값 (숫자 또는 문자열)
     */
    private Object threshold;
    
    /**
     * 기간 (예: "7d", "14d", "365d")
     */
    private String period;
    
    /**
     * 값 (임계값이 아닌 경우, 예: instance_type의 value)
     */
    private String value;
    
    /**
     * 비교 연산자 (<, >, <=, >=, ==)
     * 기본값: < (임계값 미만)
     */
    @Builder.Default
    private String operator = "<";
    
    /**
     * 기간을 일 단위로 변환
     */
    public int getPeriodInDays() {
        if (period == null || period.isEmpty()) {
            return 7; // 기본값 7일
        }
        
        // "7d" -> 7, "14d" -> 14, "365d" -> 365
        String periodStr = period.toLowerCase().trim();
        if (periodStr.endsWith("d")) {
            try {
                return Integer.parseInt(periodStr.substring(0, periodStr.length() - 1));
            } catch (NumberFormatException e) {
                return 7;
            }
        }
        
        return 7;
    }
    
    /**
     * 임계값을 숫자로 변환
     */
    public Double getThresholdAsDouble() {
        if (threshold == null) {
            return null;
        }
        
        if (threshold instanceof Number) {
            return ((Number) threshold).doubleValue();
        }
        
        if (threshold instanceof String) {
            try {
                String thresholdStr = ((String) threshold).trim();
                // "1MB" 같은 경우 처리
                if (thresholdStr.endsWith("MB")) {
                    return Double.parseDouble(thresholdStr.substring(0, thresholdStr.length() - 2)) * 1024 * 1024;
                } else if (thresholdStr.endsWith("GB")) {
                    return Double.parseDouble(thresholdStr.substring(0, thresholdStr.length() - 2)) * 1024 * 1024 * 1024;
                } else if (thresholdStr.endsWith("KB")) {
                    return Double.parseDouble(thresholdStr.substring(0, thresholdStr.length() - 2)) * 1024;
                }
                return Double.parseDouble(thresholdStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }
}

