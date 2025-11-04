package com.budgetops.backend.billing.enums;

import lombok.Getter;

/**
 * 토큰 구매 패키지 정보
 * Frontend TokenPackage와 동기화
 */
@Getter
public enum TokenPackage {
    SMALL("small", 100, 5000, 0, false),
    MEDIUM("medium", 500, 20000, 50, true),
    LARGE("large", 1000, 35000, 150, false);

    private final String id;
    private final int tokenAmount;
    private final int price;
    private final int bonusTokens;
    private final boolean popular;

    TokenPackage(String id, int tokenAmount, int price, int bonusTokens, boolean popular) {
        this.id = id;
        this.tokenAmount = tokenAmount;
        this.price = price;
        this.bonusTokens = bonusTokens;
        this.popular = popular;
    }

    /**
     * packageId로 TokenPackage 찾기
     */
    public static TokenPackage fromId(String id) {
        for (TokenPackage pkg : values()) {
            if (pkg.id.equals(id)) {
                return pkg;
            }
        }
        throw new IllegalArgumentException("Invalid package ID: " + id);
    }

    /**
     * 총 토큰 수량 (구매 토큰 + 보너스)
     */
    public int getTotalTokens() {
        return tokenAmount + bonusTokens;
    }

    /**
     * 보너스 토큰이 있는지 확인
     */
    public boolean hasBonus() {
        return bonusTokens > 0;
    }
}
