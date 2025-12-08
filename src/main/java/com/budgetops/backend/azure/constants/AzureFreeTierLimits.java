package com.budgetops.backend.azure.constants;

/**
 * Azure 프리티어 한도 및 대상 리소스 정의
 *
 * 현재는 가장 대표적인 VM 프리티어 (B1s) 기준으로만 사용량을 계산합니다.
 * - Standard_B1s: 월 750시간 무료
 *
 * 향후 Storage, Functions 등으로 확장 가능하도록 별도 클래스로 분리했습니다.
 */
public class AzureFreeTierLimits {

    private AzureFreeTierLimits() {
        // 상수 클래스
    }

    /**
     * Azure 기본 가입 크레딧 한도 (USD 기준)
     * - Azure sign-up credit 기본 200달러를 기준값으로 사용합니다.
     * - 실제 크레딧 한도가 다를 경우, 추후 계정 설정/입력을 통해 오버라이드할 수 있습니다.
     */
    public static final double AZURE_SIGNUP_CREDIT_USD = 200.0;

    /**
     * 프리티어 대상 VM Size 인지 확인
     *
     * Azure 공식 무료 계층 문서를 기준으로 B1s 계열만 우선 지원합니다.
     * (필요 시 다른 사이즈를 여기에 추가)
     *
     * @param vmSize Azure VM size (예: "Standard_B1s")
     * @return 프리티어 대상이면 true
     */
    public static boolean isFreeTierVmSize(String vmSize) {
        // VM 사이즈 기반 프리티어 판단은 더 이상 사용하지 않지만,
        // 하위 호환성을 위해 메서드는 유지합니다.
        return false;
    }
}


