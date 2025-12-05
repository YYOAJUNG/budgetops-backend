package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.constants.GcpFreeTierLimits;
import com.budgetops.backend.gcp.dto.GcpAccountDailyCostsResponse;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GCP 프리티어/크레딧 사용량 계산 서비스
 *
 * - BigQuery Billing Export에서 계산된 daily cost를 기반으로,
 *   "크레딧/프리티어로 상쇄된 금액"을 사용량으로 간주합니다.
 * - 현재는 고정 한도(300달러)를 기준으로 사용률을 계산합니다.
 */
@Service
@RequiredArgsConstructor
public class GcpFreeTierService {

    private final GcpAccountRepository accountRepository;
    private final GcpCostService costService;

    /**
     * GCP 프리티어/크레딧 사용량 조회
     *
     * @param accountId GCP 계정 ID
     * @param memberId  소유자 ID
     * @param startDate 조회 시작일 (YYYY-MM-DD)
     * @param endDate   조회 종료일 (YYYY-MM-DD, exclusive 아님 - GcpCostService에 위임)
     * @return 프리티어/크레딧 사용 요약
     */
    @Transactional(readOnly = true)
    public FreeTierUsage getFreeTierUsage(Long accountId, Long memberId, String startDate, String endDate) {
        // 계정 소유자 검증 (결과는 사용하지 않더라도 보안상 필수)
        accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("GCP 계정을 찾을 수 없습니다."));

        GcpAccountDailyCostsResponse costs = costService.getCosts(accountId, startDate, endDate);

        double usedAmount = Math.max(0.0, costs.getTotalCreditUsed());
        double limit = GcpFreeTierLimits.DEFAULT_FREE_TIER_CREDIT_LIMIT;
        double remaining = Math.max(0.0, limit - usedAmount);
        double percentage = limit > 0
                ? Math.min(100.0, (usedAmount / limit) * 100.0)
                : 0.0;

        String currency = costs.getCurrency() != null ? costs.getCurrency() : "USD";

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


