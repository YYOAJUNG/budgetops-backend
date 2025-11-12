package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 공통 사용량 메트릭 모델
 */
@Value
@Builder
public class UsageMetrics {
    Double avg;  // 평균 사용률
    Double p95;  // 95 백분위수
    Double p99;  // 99 백분위수
    Double idleRatio;  // 유휴 비율 (0~1)
    String schedulePattern;  // "weekdays", "24/7", "business-hours"
    Long uptimeDays;  // 가동 일수
    Double networkIn;  // 네트워크 인바운드 (MB)
    Double networkOut;  // 네트워크 아웃바운드 (MB)
}

