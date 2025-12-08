package com.budgetops.backend.billing.enums;

import lombok.Getter;

/**
 * 구독 상태
 */
@Getter
public enum SubscriptionStatus {
    ACTIVE("active"),       // 활성 (자동 결제 진행)
    CANCELED("canceled"),   // 취소됨 (다음 결제일까지 혜택 유지, 자동 결제 안 함)
    PAST_DUE("past_due");   // 결제 실패

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }
}
