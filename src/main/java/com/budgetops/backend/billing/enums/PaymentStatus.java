package com.budgetops.backend.billing.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    IDLE("idle", "대기"),
    PENDING("pending", "진행중"),
    FAILED("failed", "실패"),
    PAID("paid", "완료");

    private final String key;
    private final String description;

    PaymentStatus(String key, String description) {
        this.key = key;
        this.description = description;
    }

    /**
     * Iamport API에서 반환된 status 문자열로 PaymentStatus 찾기
     */
    public static PaymentStatus fromKey(String key) {
        for (PaymentStatus status : values()) {
            if (status.key.equalsIgnoreCase(key)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid payment status: " + key);
    }
}
