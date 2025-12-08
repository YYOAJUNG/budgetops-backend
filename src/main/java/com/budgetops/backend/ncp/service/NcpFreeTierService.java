package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.constants.NcpFreeTierLimits;
import com.budgetops.backend.ncp.dto.NcpCostSummary;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * NCP 프리티어/할인액 사용량 계산 서비스
 *
 * - 월별 비용 요약(NcpCostSummary)의 totalDiscountAmount를
 *   "프리티어/크레딧으로 상쇄된 금액"으로 간주합니다.
 * - 현재는 고정 한도(10만원)를 기준으로 사용률을 근사 계산합니다.
 */
@Service
@RequiredArgsConstructor
public class NcpFreeTierService {

    private final NcpAccountRepository accountRepository;
    private final NcpCostService costService;

    /**
     * NCP 프리티어/할인액 사용량 조회
     *
     * @param accountId NCP 계정 ID
     * @param memberId  소유자 ID
     * @param month     조회 월 (YYYYMM, null이면 현재 월 기준)
     * @return 프리티어/할인 사용 요약
     */
    public FreeTierUsage getFreeTierUsage(Long accountId, Long memberId, String month) {
        NcpAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("NCP 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 NCP 계정입니다.");
        }

        String targetMonth = (month != null && !month.isBlank())
                ? month
                : java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));

        NcpCostSummary summary = costService.getCostSummary(accountId, memberId, targetMonth);

        double usedAmount = summary.getTotalDiscountAmount() != null ? summary.getTotalDiscountAmount() : 0.0;
        double limit = NcpFreeTierLimits.DEFAULT_FREE_TIER_DISCOUNT_LIMIT;
        double remaining = Math.max(0.0, limit - usedAmount);
        double percentage = limit > 0
                ? Math.min(100.0, (usedAmount / limit) * 100.0)
                : 0.0;

        String currency = summary.getCurrency() != null ? summary.getCurrency() : "KRW";

        return FreeTierUsage.builder()
                .usedAmount(usedAmount)
                .freeTierLimitAmount(limit)
                .remainingAmount(remaining)
                .percentage(percentage)
                .currency(currency)
                .build();
    }

    @Value
    @Builder
    public static class FreeTierUsage {
        double usedAmount;
        double freeTierLimitAmount;
        double remainingAmount;
        double percentage;
        String currency;
    }
}


