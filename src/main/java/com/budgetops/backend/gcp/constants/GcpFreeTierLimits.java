package com.budgetops.backend.gcp.constants;

/**
 * GCP 프리티어/크레딧 한도 정의
 *
 * 현재는 가장 일반적인 무료 체험 크레딧(300달러)을 기준 한도로 사용합니다.
 * 실제 프로젝트별 무료 정책과는 차이가 있을 수 있으므로,
 * "대략 어느 정도 크레딧을 소진했는지"를 보는 용도로만 사용하는 것이 좋습니다.
 */
public class GcpFreeTierLimits {

    /**
     * 기본 프리티어/크레딧 한도 (USD 기준)
     */
    public static final double DEFAULT_FREE_TIER_CREDIT_LIMIT = 300.0;

    private GcpFreeTierLimits() {
        // 상수 클래스
    }
}


