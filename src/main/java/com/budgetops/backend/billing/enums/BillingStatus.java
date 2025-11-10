package com.budgetops.backend.billing.enums;

import lombok.Getter;

@Getter
public enum BillingStatus {
    ACTIVE("active", "활성"),
    CANCELED("canceled", "취소됨");

    private final String key;
    private final String description;

    BillingStatus(String key, String description) {
        this.key = key;
        this.description = description;
    }
}
