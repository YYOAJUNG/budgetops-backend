package com.budgetops.backend.ncp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NCP Cloud Insight 메트릭 데이터 포인트
 * [timestamp, value] 형태의 데이터
 */
@Getter
@AllArgsConstructor
public class NcpMetricDataPoint {

    /**
     * 데이터 포인트 타임스탬프 (Unix Timestamp, 밀리초)
     */
    private Long timestamp;

    /**
     * 메트릭 값
     */
    private Double value;
}
