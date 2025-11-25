package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 공통 가격 정보 모델
 */
@Value
@Builder
public class PricingInfo {
    String unit;  // "hour", "GB", "request", "slot", "GB-month"
    Double unitPrice;  // 단가
    Boolean commitmentApplicable;  // 약정 적용 가능 여부
    Double commitmentPrice;  // 약정 단가 (선택)
    String commitmentType;  // "SP", "RI", "CUD", "SavingsPlan" (선택)
}

