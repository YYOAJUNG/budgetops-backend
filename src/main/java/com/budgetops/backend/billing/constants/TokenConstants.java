package com.budgetops.backend.billing.constants;

/**
 * 토큰 관련 상수
 */
public class TokenConstants {

    /**
     * Pro 플랜의 최대 토큰 보유량
     */
    public static final int MAX_TOKEN_LIMIT = 100000;

    /**
     * Free 플랜의 최대 토큰 보유량
     */
    public static final int FREE_PLAN_MAX_TOKENS = 10000;

    private TokenConstants() {
        // 유틸리티 클래스, 인스턴스화 방지
    }
}
