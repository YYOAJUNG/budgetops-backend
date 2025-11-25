package com.budgetops.backend.ncp.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * NCP 비용 요약 정보
 */
@Getter
@Setter
@Builder
public class NcpCostSummary {
    /**
     * 조회 월 (YYYYMM 형식)
     */
    private String month;

    /**
     * 총 비용
     */
    private Double totalCost;

    /**
     * 통화
     */
    private String currency;

    /**
     * 총 청구 금액 (demandAmount 합계)
     */
    private Double totalDemandAmount;

    /**
     * 총 사용 금액 (useAmount 합계)
     */
    private Double totalUseAmount;

    /**
     * 총 할인 금액
     */
    private Double totalDiscountAmount;
}
