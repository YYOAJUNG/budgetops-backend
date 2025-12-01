package com.budgetops.backend.budget.service;

import com.budgetops.backend.budget.dto.BudgetAlertResponse;
import com.budgetops.backend.budget.dto.BudgetSettingsRequest;
import com.budgetops.backend.budget.dto.BudgetSettingsResponse;
import com.budgetops.backend.budget.dto.BudgetUsageResponse;
import com.budgetops.backend.budget.entity.MemberAccountBudget;
import com.budgetops.backend.budget.repository.MemberAccountBudgetRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final MemberRepository memberRepository;
    private final CloudCostAggregator cloudCostAggregator;
    private final MemberAccountBudgetRepository memberAccountBudgetRepository;

    @Transactional(readOnly = true)
    public BudgetSettingsResponse getSettings(Long memberId) {
        Member member = getMember(memberId);
        List<MemberAccountBudget> accountBudgets = memberAccountBudgetRepository.findByMemberId(memberId);
        return new BudgetSettingsResponse(
                member.getMonthlyBudgetLimit(),
                member.getBudgetAlertThreshold(),
                member.getUpdatedAt(),
                accountBudgets.stream()
                        .map(budget -> new BudgetSettingsResponse.MemberAccountBudgetView(
                                budget.getProvider(),
                                budget.getAccountId(),
                                budget.getMonthlyBudgetLimit(),
                                budget.getAlertThreshold()
                        ))
                        .toList()
        );
    }

    @Transactional
    public BudgetSettingsResponse updateSettings(Long memberId, BudgetSettingsRequest request) {
        Member member = getMember(memberId);
        member.setMonthlyBudgetLimit(
                request.monthlyBudgetLimit().setScale(2, RoundingMode.HALF_UP)
        );
        member.setBudgetAlertThreshold(request.alertThreshold());
        member.setBudgetAlertTriggeredAt(null); // 설정 변경 시 알림 재활성화
        Member saved = memberRepository.save(member);

        // 계정별 예산 설정 갱신
        List<BudgetSettingsRequest.MemberAccountBudgetSetting> requestedBudgets =
                Optional.ofNullable(request.accountBudgets()).orElse(Collections.emptyList());

        // 기존 설정을 전부 삭제하고, 새 설정으로 교체 (간단한 동작 보장)
        memberAccountBudgetRepository.deleteByMemberId(memberId);

        for (BudgetSettingsRequest.MemberAccountBudgetSetting setting : requestedBudgets) {
            if (setting.monthlyBudgetLimit() == null) {
                continue;
            }
            if (setting.monthlyBudgetLimit().compareTo(BigDecimal.ZERO) <= 0) {
                // 0 이하 예산은 저장하지 않음 (프론트에서도 필터링하지만 이중 방어)
                continue;
            }

            MemberAccountBudget budget = MemberAccountBudget.builder()
                    .member(member)
                    .provider(setting.provider())
                    .accountId(setting.accountId())
                    .monthlyBudgetLimit(setting.monthlyBudgetLimit().setScale(2, RoundingMode.HALF_UP))
                    .alertThreshold(setting.alertThreshold())
                    .build();
            memberAccountBudgetRepository.save(budget);
        }

        List<MemberAccountBudget> updatedBudgets = memberAccountBudgetRepository.findByMemberId(memberId);

        return new BudgetSettingsResponse(
                saved.getMonthlyBudgetLimit(),
                saved.getBudgetAlertThreshold(),
                saved.getUpdatedAt(),
                updatedBudgets.stream()
                        .map(budget -> new BudgetSettingsResponse.MemberAccountBudgetView(
                                budget.getProvider(),
                                budget.getAccountId(),
                                budget.getMonthlyBudgetLimit(),
                                budget.getAlertThreshold()
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public BudgetUsageResponse getUsage(Long memberId) {
        Member member = getMember(memberId);
        return computeUsage(member).usage();
    }

    @Transactional
    public Optional<BudgetAlertResponse> checkAlert(Long memberId) {
        Member member = getMember(memberId);
        UsageComputation computation = computeUsage(member);
        BudgetUsageResponse usage = computation.usage();
        resetAlertIfNewMonth(member, usage.month());

        if (!usage.thresholdReached()) {
            return Optional.empty();
        }

        if (alreadyTriggeredThisMonth(member, usage.month())) {
            return Optional.empty();
        }

        member.setBudgetAlertTriggeredAt(LocalDateTime.now());
        memberRepository.save(member);
        log.info("Budget alert triggered for member {} usage={}%", memberId, usage.usagePercentage());

        BudgetAlertResponse response = new BudgetAlertResponse(
                usage.monthlyBudgetLimit(),
                usage.currentMonthCost(),
                usage.usagePercentage(),
                usage.alertThreshold() == null ? 0 : usage.alertThreshold(),
                usage.month(),
                usage.currency(),
                member.getBudgetAlertTriggeredAt(),
                buildAlertMessage(usage)
        );
        return Optional.of(response);
    }

    private UsageComputation computeUsage(Member member) {
        CloudCostAggregator.CloudCostSnapshot snapshot = cloudCostAggregator.calculateCurrentMonth(member.getId());
        BigDecimal budget = normalizeBudget(member.getMonthlyBudgetLimit());
        Integer threshold = member.getBudgetAlertThreshold();
        BigDecimal totalCost = snapshot.totalKrw().setScale(2, RoundingMode.HALF_UP);

        double usagePercentage = 0.0;
        if (budget.compareTo(BigDecimal.ZERO) > 0) {
            usagePercentage = totalCost
                    .divide(budget, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        boolean thresholdReached = threshold != null && threshold > 0 && usagePercentage >= threshold;

        // 계정별 사용량 계산
        List<BudgetUsageResponse.AccountUsage> accountUsages = buildAccountUsages(member.getId(), snapshot, budgetSettingsFor(member));

        BudgetUsageResponse usageResponse = new BudgetUsageResponse(
                budget,
                threshold,
                totalCost,
                usagePercentage,
                thresholdReached,
                snapshot.month(),
                "KRW",
                accountUsages
        );
        return new UsageComputation(usageResponse, snapshot);
    }

    private String buildAlertMessage(BudgetUsageResponse usage) {
        return String.format(
                "이번 달 비용이 설정한 예산의 %.1f%%에 도달했습니다. (임계값 %d%%)",
                usage.usagePercentage(),
                usage.alertThreshold()
        );
    }

    private void resetAlertIfNewMonth(Member member, String currentMonth) {
        if (member.getBudgetAlertTriggeredAt() == null) {
            return;
        }
        String triggeredMonth = member.getBudgetAlertTriggeredAt().format(MONTH_KEY);
        if (!triggeredMonth.equals(currentMonth)) {
            member.setBudgetAlertTriggeredAt(null);
            memberRepository.save(member);
        }
    }

    private boolean alreadyTriggeredThisMonth(Member member, String currentMonth) {
        if (member.getBudgetAlertTriggeredAt() == null) {
            return false;
        }
        String triggeredMonth = member.getBudgetAlertTriggeredAt().format(MONTH_KEY);
        return triggeredMonth.equals(currentMonth);
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
    }

    private BigDecimal normalizeBudget(BigDecimal budget) {
        if (budget == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return budget.setScale(2, RoundingMode.HALF_UP);
    }

    private record UsageComputation(
            BudgetUsageResponse usage,
            CloudCostAggregator.CloudCostSnapshot snapshot
    ) {
    }

    /**
     * 계정별 예산 설정과 실제 비용 스냅샷을 조합해 계정별 사용량 리스트 생성
     */
    private List<BudgetUsageResponse.AccountUsage> buildAccountUsages(
            Long memberId,
            CloudCostAggregator.CloudCostSnapshot snapshot,
            List<MemberAccountBudget> budgets
    ) {
        if (snapshot.accountCosts() == null || snapshot.accountCosts().isEmpty()) {
            return List.of();
        }

        // (provider, accountId) 기준으로 예산 매핑
        var budgetMap = budgets.stream().collect(
                java.util.stream.Collectors.toMap(
                        b -> key(b.getProvider(), b.getAccountId()),
                        b -> b
                )
        );

        List<BudgetUsageResponse.AccountUsage> result = new java.util.ArrayList<>();

        for (CloudCostAggregator.AccountCostSnapshot cost : snapshot.accountCosts()) {
            String provider = cost.provider();
            Long accountId = cost.accountId();
            String key = key(provider, accountId);
            MemberAccountBudget budget = budgetMap.get(key);

            BigDecimal currentCost = cost.costKrw().setScale(2, RoundingMode.HALF_UP);
            BigDecimal monthlyLimit = budget != null
                    ? budget.getMonthlyBudgetLimit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            Integer accountThreshold = budget != null ? budget.getAlertThreshold() : null;

            double accountUsagePercentage = 0.0;
            if (monthlyLimit.compareTo(BigDecimal.ZERO) > 0) {
                accountUsagePercentage = currentCost
                        .divide(monthlyLimit, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            boolean accountThresholdReached =
                    accountThreshold != null && accountThreshold > 0 && accountUsagePercentage >= accountThreshold;

            result.add(new BudgetUsageResponse.AccountUsage(
                    provider,
                    accountId,
                    cost.accountName(),
                    currentCost,
                    monthlyLimit,
                    accountThreshold,
                    accountUsagePercentage,
                    accountThresholdReached,
                    "KRW"
            ));
        }

        return result;
    }

    private List<MemberAccountBudget> budgetSettingsFor(Member member) {
        return memberAccountBudgetRepository.findByMemberId(member.getId());
    }

    private String key(String provider, Long accountId) {
        return provider + ":" + accountId;
    }
}

