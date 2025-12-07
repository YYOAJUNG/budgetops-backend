package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.constants.GcpFreeTierLimits;
import com.budgetops.backend.gcp.dto.GcpAccountDailyCostsResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * GCP 프리티어/크레딧 사용량 계산 서비스
 *
 * - BigQuery Billing Export에서 계산된 daily cost를 기반으로,
 *   "크레딧/프리티어로 상쇄된 금액"을 사용량으로 간주합니다.
 * - 한도/기간은 계정 설정 및 쿼리 파라미터를 통해 설정할 수 있습니다.
 */
@Service
@RequiredArgsConstructor
public class GcpFreeTierService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final GcpAccountRepository accountRepository;
    private final GcpCostService costService;

    /**
     * GCP 프리티어/크레딧 사용량 조회
     *
     * @param accountId         GCP 계정 ID
     * @param memberId          소유자 ID
     * @param analysisStartDate 분석에 사용할 시작일 (null이면 크레딧 시작일 또는 기본값)
     * @param analysisEndDate   분석에 사용할 종료일 (null이면 크레딧 종료일 또는 기본값, GcpCostService에는 exclusive로 전달)
     * @param creditLimitAmount 크레딧 한도 (null이면 계정 설정 또는 기본 300 USD 사용)
     * @param creditStartDate   크레딧 시작일 (null이면 계정 설정 또는 오늘)
     * @param creditEndDate     크레딧 종료일 (null이면 계정 설정 또는 시작일 + 1개월)
     * @return 프리티어/크레딧 사용 요약
     */
    @Transactional(readOnly = true)
    public FreeTierUsage getFreeTierUsage(
            Long accountId,
            Long memberId,
            LocalDate analysisStartDate,
            LocalDate analysisEndDate,
            Double creditLimitAmount,
            LocalDate creditStartDate,
            LocalDate creditEndDate
    ) {
        GcpAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("GCP 계정을 찾을 수 없습니다."));

        // 크레딧을 사용하지 않는 계정인 경우 0으로 반환
        if (Boolean.FALSE.equals(account.getHasCredit())) {
            return FreeTierUsage.builder()
                    .usedAmount(0.0)
                    .freeTierLimitAmount(0.0)
                    .remainingAmount(0.0)
                    .percentage(0.0)
                    .currency("USD")
                    .build();
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveCreditStart = creditStartDate != null
                ? creditStartDate
                : (account.getCreditStartDate() != null ? account.getCreditStartDate() : today);
        LocalDate effectiveCreditEnd = creditEndDate != null
                ? creditEndDate
                : (account.getCreditEndDate() != null ? account.getCreditEndDate() : effectiveCreditStart.plusMonths(1));

        if (effectiveCreditEnd.isBefore(effectiveCreditStart)) {
            throw new IllegalArgumentException("크레딧 종료일은 시작일 이후여야 합니다.");
        }

        LocalDate effectiveAnalysisStart = analysisStartDate != null ? analysisStartDate : effectiveCreditStart;
        LocalDate effectiveAnalysisEnd = analysisEndDate != null ? analysisEndDate : effectiveCreditEnd;

        if (effectiveAnalysisEnd.isBefore(effectiveAnalysisStart)) {
            throw new IllegalArgumentException("분석 종료일은 시작일 이후여야 합니다.");
        }

        String startDate = effectiveAnalysisStart.format(ISO_DATE);
        // GcpCostService는 endDate를 exclusive로 사용하므로 그대로 전달
        String endDate = effectiveAnalysisEnd.format(ISO_DATE);

        GcpAccountDailyCostsResponse costs = costService.getCosts(accountId, startDate, endDate);

        double usedAmount = Math.max(0.0, costs.getTotalCreditUsed());

        double limit = creditLimitAmount != null && creditLimitAmount > 0
                ? creditLimitAmount
                : (account.getCreditLimitAmount() != null && account.getCreditLimitAmount() > 0
                    ? account.getCreditLimitAmount()
                    : GcpFreeTierLimits.DEFAULT_FREE_TIER_CREDIT_LIMIT);

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


