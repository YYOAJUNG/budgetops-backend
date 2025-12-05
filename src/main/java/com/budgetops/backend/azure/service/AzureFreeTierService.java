package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.constants.AzureFreeTierLimits;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Azure 프리티어 사용량 계산 서비스
 *
 * 참고:
 * - 실제 Azure 포털의 크레딧/프리티어 잔액과 1:1로 일치하지 않습니다.
 * - VM 실행 시간을 모두 추적하는 대신,
 *   조회 기간 동안 "모든 VM 수 × 기간(시간)" 을 기반으로
 *   일반적인 B1s 프리티어 한도(월 750시간)를 스케일링하여 근사 계산합니다.
 *   (24시간 상시 실행된다고 가정한 대략적인 사용률 지표 용도)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AzureFreeTierService {

    private final AzureAccountRepository accountRepository;
    private final AzureComputeService computeService;

    /**
     * Azure VM 프리티어 사용량 조회 (근사치)
     *
     * @param accountId Azure 계정 ID
     * @param memberId  소유자 ID
     * @param startDate 조회 시작일 (포함)
     * @param endDate   조회 종료일 (포함)
     * @return 프리티어 사용 요약
     */
    @Transactional(readOnly = true)
    public FreeTierUsage getVmFreeTierUsage(Long accountId, Long memberId, LocalDate startDate, LocalDate endDate) {
        AzureAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("Azure 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 Azure 계정입니다.");
        }

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate와 endDate는 필수입니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate는 startDate 이후여야 합니다.");
        }

        // 조회 기간의 총 시간 (포함 범위: [startDate, endDate])
        double hoursInPeriod = ChronoUnit.HOURS.between(
                startDate.atStartOfDay(ZoneId.systemDefault()),
                endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault())
        );

        // 계정의 VM 목록 조회 (위치 필터 없이 전체)
        List<com.budgetops.backend.azure.dto.AzureVirtualMachineResponse> vms =
                computeService.listVirtualMachines(accountId, null);

        int vmCount = vms.size();

        if (vmCount == 0) {
            return FreeTierUsage.builder()
                    .totalUsageHours(0.0)
                    .freeTierLimitHours(AzureFreeTierLimits.VM_FREE_TIER_HOURS_PER_MONTH)
                    .remainingHours(AzureFreeTierLimits.VM_FREE_TIER_HOURS_PER_MONTH)
                    .percentage(0.0)
                    .eligibleVmCount(0)
                    .build();
        }

        // 모든 VM을 프리티어/크레딧을 소모하는 대상으로 보고,
        // VM 수에 비례하여 프리티어 한도를 확장 (VM 당 750시간 기준)
        double totalUsageHours = vmCount * hoursInPeriod;
        double freeTierLimit = AzureFreeTierLimits.VM_FREE_TIER_HOURS_PER_MONTH * vmCount;
        double remaining = Math.max(0.0, freeTierLimit - totalUsageHours);
        double percentage = freeTierLimit > 0
                ? Math.min(100.0, (totalUsageHours / freeTierLimit) * 100.0)
                : 0.0;

        return FreeTierUsage.builder()
                .totalUsageHours(totalUsageHours)
                .freeTierLimitHours(freeTierLimit)
                .remainingHours(remaining)
                .percentage(percentage)
                .eligibleVmCount(vmCount)
                .build();
    }

    @Value
    @Builder
    public static class FreeTierUsage {
        double totalUsageHours;
        double freeTierLimitHours;
        double remainingHours;
        double percentage;
        int eligibleVmCount;
    }
}


