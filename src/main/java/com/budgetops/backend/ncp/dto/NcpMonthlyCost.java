package com.budgetops.backend.ncp.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NcpMonthlyCost {
    private String demandMonth; // YYYYMM 형식
    private String demandType;
    private String demandTypeDetail;
    private String contractNo;
    private String instanceName;
    private Double demandAmount; // 청구 금액
    private Double useAmount; // 사용 금액
    private Double promotionDiscountAmount; // 프로모션 할인
    private Double etcDiscountAmount; // 기타 할인
    private String currency; // KRW, USD 등
}
