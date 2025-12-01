package com.budgetops.backend.budget.service;

import com.budgetops.backend.budget.dto.BudgetAlertResponse;
import com.budgetops.backend.budget.dto.BudgetSettingsRequest;
import com.budgetops.backend.budget.dto.BudgetSettingsResponse;
import com.budgetops.backend.budget.dto.BudgetUsageResponse;
import com.budgetops.backend.billing.entity.Member;
import com.budgetops.backend.billing.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final MemberRepository memberRepository;
    private final CloudCostAggregator cloudCostAggregator;

    @Transactional(readOnly = true)
    public BudgetSettingsResponse getSettings(Long memberId) {
        Member member = getMember(memberId);
        return new BudgetSettingsResponse(
                member.getMonthlyBudgetLimit(),
                member.getBudgetAlertThreshold(),
                member.getUpdatedAt()
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
        return new BudgetSettingsResponse(
                saved.getMonthlyBudgetLimit(),
                saved.getBudgetAlertThreshold(),
                saved.getUpdatedAt()
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

        BudgetUsageResponse usageResponse = new BudgetUsageResponse(
                budget,
                threshold,
                totalCost,
                usagePercentage,
                thresholdReached,
                snapshot.month(),
                "KRW"
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
}

