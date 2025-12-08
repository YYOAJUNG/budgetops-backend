package com.budgetops.backend.ncp.constants;

/**
 * NCP 프리티어/할인 한도 정의
 *
 * - NCP는 서비스별로 다양한 프로모션/크레딧 정책이 있으므로,
 *   여기서는 "총 할인액(프로모션 + 기타 할인)"을 하나의 크레딧 풀로 보고,
 *   기준 한도 대비 얼마나 사용했는지 근사 계산합니다.
 */
public class NcpFreeTierLimits {

    /**
     * 기본 프리티어/할인 한도 (KRW 기준, 근사값)
     * 필요시 운영 과정에서 조정 가능하도록 상수로 분리했습니다.
     */
    public static final double DEFAULT_FREE_TIER_DISCOUNT_LIMIT = 100_000.0; // 10만원

    private NcpFreeTierLimits() {
        // 상수 클래스
    }
}


