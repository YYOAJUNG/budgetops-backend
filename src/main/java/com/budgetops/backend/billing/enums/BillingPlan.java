package com.budgetops.backend.billing.enums;

import lombok.Getter;

@Getter
public enum BillingPlan {
    FREE("Free", 0, 10000),      // AI 어시스턴트 기본 할당량 (10k 토큰)
    PRO("Pro", 4900, 30000);     // AI 어시스턴트 확장 할당량 (30k 토큰 = 기본 10k + Pro 추가 20k)

    private final String displayName;
    private final int monthlyPrice;        // 월 고정 가격 (원화 KRW)
    private final int aiAssistantQuota;    // AI 어시스턴트 월 사용 할당량 (토큰)

    BillingPlan(String displayName, int monthlyPrice, int aiAssistantQuota) {
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.aiAssistantQuota = aiAssistantQuota;
    }

    /**
     * 무료 요금제인지 확인
     */
    public boolean isFree() {
        return this == FREE;
    }

    /**
     * 총 요금 반환 (월 고정 가격)
     */
    public int getTotalPrice() {
        return monthlyPrice;
    }

    /**
     * AI 어시스턴트 월 사용 할당량 반환
     */
    public int getAiAssistantQuota() {
        return aiAssistantQuota;
    }
}
