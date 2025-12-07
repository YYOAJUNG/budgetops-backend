package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.constants.AzureFreeTierLimits;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import lombok.Builder;
import lombok.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Azure 크레딧/프리티어 사용량 계산 서비스
 *
 * - Azure 포털의 sign-up credit 잔액과 1:1로 일치하지는 않지만,
 *   Cost Management API에서 가져온 비용 합계를 기준으로
 *   "설정된 크레딧 한도 대비 어느 정도 사용했는지"를 근사 계산합니다.
 * - 한도/기간은 쿼리 파라미터로 전달되며, 미지정 시 기본값을 사용합니다.
 */
@Service
@RequiredArgsConstructor
public class AzureFreeTierService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AzureAccountRepository accountRepository;
    private final AzureCostService costService;

    /**
     * Azure 크레딧 기반 프리티어 사용량 조회 (근사치)
     *
     * @param accountId          Azure 계정 ID
     * @param memberId           소유자 ID
     * @param analysisStartDate  비용 분석에 사용할 시작일 (null이면 creditStartDate와 동일하게 처리)
     * @param analysisEndDate    비용 분석에 사용할 종료일 (null이면 creditEndDate와 동일하게 처리)
     * @param creditLimitAmount  크레딧 한도 (미지정 시 기본 200 USD 가정)
     * @param creditStartDate    크레딧 시작일 (미지정 시 오늘)
     * @param creditEndDate      크레딧 종료일 (미지정 시 creditStartDate + 1개월)
     * @return 크레딧 사용 요약
     */
    @Transactional(readOnly = true)
    public FreeTierUsage getCreditFreeTierUsage(
            Long accountId,
            Long memberId,
            LocalDate analysisStartDate,
            LocalDate analysisEndDate,
            Double creditLimitAmount,
            LocalDate creditStartDate,
            LocalDate creditEndDate
    ) {
        // 계정 및 소유자 검증
        AzureAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("Azure 계정을 찾을 수 없습니다."));

        // 크레딧을 사용하지 않는 계정인 경우 0으로 반환
        if (Boolean.FALSE.equals(account.getHasCredit())) {
            return FreeTierUsage.builder()
                    .usedAmount(0.0)
                    .creditLimitAmount(0.0)
                    .remainingAmount(0.0)
                    .percentage(0.0)
                    .currency("USD")
                    .creditStartDate(null)
                    .creditEndDate(null)
                    .build();
        }

        // 크레딧 기간 기본값 설정 (우선순위: 파라미터 > 계정 설정 > 오늘 기준 기본값)
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

        // 분석 기간이 별도로 없으면 크레딧 기간과 동일하게 사용
        LocalDate effectiveAnalysisStart = analysisStartDate != null ? analysisStartDate : effectiveCreditStart;
        LocalDate effectiveAnalysisEnd = analysisEndDate != null ? analysisEndDate : effectiveCreditEnd;

        if (effectiveAnalysisEnd.isBefore(effectiveAnalysisStart)) {
            throw new IllegalArgumentException("분석 종료일은 시작일 이후여야 합니다.");
        }

        String from = effectiveAnalysisStart.format(ISO_DATE);
        String to = effectiveAnalysisEnd.plusDays(1).format(ISO_DATE); // Azure Cost API endDate는 exclusive

        // 지정된 기간 동안의 일별 비용 조회
        List<AzureCostService.DailyCost> dailyCosts = costService.getCosts(accountId, memberId, from, to);

        double usedAmount = dailyCosts.stream()
                .mapToDouble(AzureCostService.DailyCost::getAmount)
                .sum();

        String currency = dailyCosts.stream()
                .map(AzureCostService.DailyCost::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("USD");

        double limit = creditLimitAmount != null && creditLimitAmount > 0
                ? creditLimitAmount
                : (account.getCreditLimitAmount() != null && account.getCreditLimitAmount() > 0
                    ? account.getCreditLimitAmount()
                    : AzureFreeTierLimits.AZURE_SIGNUP_CREDIT_USD);

        double remaining = Math.max(0.0, limit - usedAmount);
        double percentage = limit > 0
                ? Math.min(100.0, (usedAmount / limit) * 100.0)
                : 0.0;

        return FreeTierUsage.builder()
                .usedAmount(usedAmount)
                .creditLimitAmount(limit)
                .remainingAmount(remaining)
                .percentage(percentage)
                .currency(currency)
                .creditStartDate(effectiveCreditStart)
                .creditEndDate(effectiveCreditEnd)
                .build();
    }

    @Value
    @Builder
    public static class FreeTierUsage {
        double usedAmount;
        double creditLimitAmount;
        double remainingAmount;
        double percentage;
        String currency;
        LocalDate creditStartDate;
        LocalDate creditEndDate;
    }
}

