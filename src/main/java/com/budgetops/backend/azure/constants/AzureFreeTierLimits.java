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

    /**
     * VM 프리티어 한도 (시간 단위)
     * - Standard_B1s 기준 월 750시간
     */
    public static final double VM_FREE_TIER_HOURS_PER_MONTH = 750.0;

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
        if (vmSize == null || vmSize.isBlank()) {
            return false;
        }
        String normalized = vmSize.trim().toLowerCase();
        return normalized.equals("standard_b1s");
    }
}


