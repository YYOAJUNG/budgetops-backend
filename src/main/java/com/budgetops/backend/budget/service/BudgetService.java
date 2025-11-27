package com.budgetops.backend.budget.service;

import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.budget.dto.BudgetAlertResponse;
import com.budgetops.backend.budget.dto.BudgetSettingsRequest;
import com.budgetops.backend.budget.dto.BudgetSettingsResponse;
import com.budgetops.backend.budget.dto.BudgetUsageResponse;
import com.budgetops.backend.budget.entity.AccountBudgetSetting;
import com.budgetops.backend.budget.model.BudgetMode;
import com.budgetops.backend.budget.model.CloudProvider;
import com.budgetops.backend.budget.repository.AccountBudgetSettingRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final MemberRepository memberRepository;
    private final AccountBudgetSettingRepository accountBudgetSettingRepository;
    private final AwsAccountRepository awsAccountRepository;
    private final AzureAccountRepository azureAccountRepository;
    private final GcpAccountRepository gcpAccountRepository;
    private final NcpAccountRepository ncpAccountRepository;
    private final CloudCostAggregator cloudCostAggregator;

    @Transactional(readOnly = true)
    public BudgetSettingsResponse getSettings(Long memberId) {
        Member member = getMember(memberId);
        List<AccountBudgetSetting> accountSettings = accountBudgetSettingRepository.findByMemberId(memberId);
        return toSettingsResponse(member, accountSettings);
    }

    @Transactional
    public BudgetSettingsResponse updateSettings(Long memberId, BudgetSettingsRequest request) {
        Member member = getMember(memberId);
        member.setBudgetMode(Optional.ofNullable(request.mode()).orElse(BudgetMode.CONSOLIDATED));
        member.setMonthlyBudgetLimit(normalizeBudget(request.monthlyBudgetLimit()));
        member.setBudgetAlertThreshold(request.alertThreshold());
        member.setBudgetAlertTriggeredAt(null);
        Member saved = memberRepository.save(member);

        List<AccountBudgetSetting> updatedAccountBudgets = syncAccountBudgetSettings(
                saved,
                Optional.ofNullable(request.accountBudgets()).orElseGet(List::of)
        );

        return toSettingsResponse(saved, updatedAccountBudgets);
    }

    @Transactional(readOnly = true)
    public BudgetUsageResponse getUsage(Long memberId) {
        Member member = getMember(memberId);
        return computeUsage(member).usage();
    }

    @Transactional
    public List<BudgetAlertResponse> checkAlerts(Long memberId) {
        Member member = getMember(memberId);
        UsageComputation computation = computeUsage(member);
        BudgetUsageResponse usage = computation.usage();
        resetMemberAlertIfNewMonth(member, usage.month());

        List<BudgetAlertResponse> alerts = new ArrayList<>();

        if (usage.thresholdReached() && !alreadyTriggeredMemberThisMonth(member, usage.month())) {
            member.setBudgetAlertTriggeredAt(LocalDateTime.now());
            memberRepository.save(member);
            log.info("Budget alert triggered for member {} usage={}%", memberId, usage.usagePercentage());
            alerts.add(buildMemberAlertResponse(member, usage));
        }

        computation.accountBudgets().values().forEach(setting -> resetAccountAlertIfNewMonth(setting, usage.month()));

        for (BudgetUsageResponse.AccountBudgetUsage accountUsage : usage.accountUsages()) {
            if (!accountUsage.hasBudget() || !accountUsage.thresholdReached()) {
                continue;
            }
            AccountKey key = new AccountKey(accountUsage.provider(), accountUsage.accountId());
            AccountBudgetSetting setting = computation.accountBudgets().get(key);
            if (setting == null || alreadyTriggeredAccountThisMonth(setting, usage.month())) {
                continue;
            }
            LocalDateTime triggeredAt = LocalDateTime.now();
            setting.setAlertTriggeredAt(triggeredAt);
            accountBudgetSettingRepository.save(setting);
            alerts.add(buildAccountAlertResponse(member, accountUsage, usage.month(), usage.currency(), triggeredAt));
        }

        return alerts;
    }

    private BudgetSettingsResponse toSettingsResponse(Member member, List<AccountBudgetSetting> accountSettings) {
        List<BudgetSettingsResponse.AccountBudgetSettingResponse> responses = accountSettings.stream()
                .sorted(Comparator
                        .comparing(AccountBudgetSetting::getProvider)
                        .thenComparing(AccountBudgetSetting::getAccountNameSnapshot, Comparator.nullsLast(String::compareTo)))
                .map(setting -> new BudgetSettingsResponse.AccountBudgetSettingResponse(
                        setting.getProvider(),
                        setting.getProviderAccountId(),
                        setting.getAccountNameSnapshot(),
                        setting.getMonthlyBudgetLimit(),
                        setting.getAlertThreshold()
                ))
                .toList();

        return new BudgetSettingsResponse(
                member.getBudgetMode(),
                normalizeBudget(member.getMonthlyBudgetLimit()),
                member.getBudgetAlertThreshold(),
                member.getUpdatedAt(),
                responses
        );
    }

    private List<AccountBudgetSetting> syncAccountBudgetSettings(
            Member member,
            List<BudgetSettingsRequest.AccountBudgetSettingRequest> requests
    ) {
        Map<AccountKey, AccountBudgetSetting> existing = new HashMap<>();
        accountBudgetSettingRepository.findByMemberId(member.getId())
                .forEach(setting -> existing.put(new AccountKey(setting.getProvider(), setting.getProviderAccountId()), setting));

        List<AccountBudgetSetting> updated = new ArrayList<>();

        for (BudgetSettingsRequest.AccountBudgetSettingRequest req : requests) {
            AccountKey key = new AccountKey(req.provider(), req.accountId());
            AccountBudgetSetting setting = existing.getOrDefault(key, new AccountBudgetSetting());
            setting.setMember(member);
            setting.setProvider(req.provider());
            setting.setProviderAccountId(req.accountId());
            setting.setMonthlyBudgetLimit(normalizeBudget(req.monthlyBudgetLimit()));
            setting.setAlertThreshold(req.alertThreshold());
            setting.setAccountNameSnapshot(resolveAccountName(req.provider(), req.accountId(), member.getId()));
            setting.setAlertTriggeredAt(null);

            AccountBudgetSetting saved = accountBudgetSettingRepository.save(setting);
            updated.add(saved);
            existing.remove(key);
        }

        existing.values().forEach(accountBudgetSettingRepository::delete);
        return updated;
    }

    private String resolveAccountName(CloudProvider provider, Long accountId, Long memberId) {
        String name = switch (provider) {
            case AWS -> awsAccountRepository.findByIdAndOwnerId(accountId, memberId)
                    .orElseThrow(() -> new IllegalArgumentException("AWS 계정을 찾을 수 없습니다. id=" + accountId))
                    .getName();
            case AZURE -> azureAccountRepository.findByIdAndOwnerId(accountId, memberId)
                    .orElseThrow(() -> new IllegalArgumentException("Azure 계정을 찾을 수 없습니다. id=" + accountId))
                    .getName();
            case GCP -> gcpAccountRepository.findByIdAndOwnerId(accountId, memberId)
                    .orElseThrow(() -> new IllegalArgumentException("GCP 계정을 찾을 수 없습니다. id=" + accountId))
                    .getName();
            case NCP -> ncpAccountRepository.findByIdAndOwnerId(accountId, memberId)
                    .orElseThrow(() -> new IllegalArgumentException("NCP 계정을 찾을 수 없습니다. id=" + accountId))
                    .getName();
        };
        return safeAccountName(name, provider, accountId);
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

        Map<AccountKey, AccountBudgetSetting> accountBudgets = new HashMap<>();
        accountBudgetSettingRepository.findByMemberId(member.getId())
                .forEach(setting -> accountBudgets.put(new AccountKey(setting.getProvider(), setting.getProviderAccountId()), setting));

        List<CloudCostAggregator.AccountCostSnapshot> accountCosts = snapshot.accountCosts() != null
                ? snapshot.accountCosts()
                : List.of();
        List<BudgetUsageResponse.AccountBudgetUsage> accountUsages = buildAccountUsages(accountCosts, accountBudgets);

        BudgetUsageResponse usageResponse = new BudgetUsageResponse(
                member.getBudgetMode(),
                budget,
                threshold,
                totalCost,
                usagePercentage,
                thresholdReached,
                snapshot.month(),
                "KRW",
                accountUsages
        );
        return new UsageComputation(usageResponse, snapshot, accountBudgets);
    }

    private List<BudgetUsageResponse.AccountBudgetUsage> buildAccountUsages(
            List<CloudCostAggregator.AccountCostSnapshot> accountCosts,
            Map<AccountKey, AccountBudgetSetting> accountBudgets
    ) {
        Map<AccountKey, BudgetUsageResponse.AccountBudgetUsage> usages = new HashMap<>();

        for (CloudCostAggregator.AccountCostSnapshot accountCost : accountCosts) {
            AccountKey key = new AccountKey(accountCost.provider(), accountCost.accountId());
            AccountBudgetSetting setting = accountBudgets.get(key);
            BigDecimal limit = setting != null ? normalizeBudget(setting.getMonthlyBudgetLimit()) : null;
            Integer threshold = setting != null ? setting.getAlertThreshold() : null;
            double usagePercentage = 0.0;
            boolean reached = false;
            if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0 && threshold != null) {
                usagePercentage = accountCost.costKrw()
                        .divide(limit, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                reached = usagePercentage >= threshold;
            }
            usages.put(key, new BudgetUsageResponse.AccountBudgetUsage(
                    accountCost.provider(),
                    accountCost.accountId(),
                    accountCost.accountName(),
                    accountCost.costKrw(),
                    limit,
                    threshold,
                    usagePercentage,
                    reached,
                    setting != null
            ));
        }

        accountBudgets.forEach((key, setting) -> usages.computeIfAbsent(key, k ->
                new BudgetUsageResponse.AccountBudgetUsage(
                        setting.getProvider(),
                        setting.getProviderAccountId(),
                        setting.getAccountNameSnapshot(),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        normalizeBudget(setting.getMonthlyBudgetLimit()),
                        setting.getAlertThreshold(),
                        0.0,
                        false,
                        true
                )
        ));

        List<BudgetUsageResponse.AccountBudgetUsage> result = new ArrayList<>(usages.values());
        result.sort(Comparator
                .comparing(BudgetUsageResponse.AccountBudgetUsage::provider)
                .thenComparing(BudgetUsageResponse.AccountBudgetUsage::accountName, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    private BudgetAlertResponse buildMemberAlertResponse(Member member, BudgetUsageResponse usage) {
        return new BudgetAlertResponse(
                member.getBudgetMode(),
                null,
                null,
                null,
                usage.monthlyBudgetLimit(),
                usage.currentMonthCost(),
                usage.usagePercentage(),
                usage.alertThreshold() == null ? 0 : usage.alertThreshold(),
                usage.month(),
                usage.currency(),
                member.getBudgetAlertTriggeredAt(),
                buildMemberAlertMessage(usage)
        );
    }

    private BudgetAlertResponse buildAccountAlertResponse(
            Member member,
            BudgetUsageResponse.AccountBudgetUsage accountUsage,
            String month,
            String currency,
            LocalDateTime triggeredAt
    ) {
        return new BudgetAlertResponse(
                member.getBudgetMode(),
                accountUsage.provider(),
                accountUsage.accountId(),
                accountUsage.accountName(),
                accountUsage.monthlyBudgetLimit(),
                accountUsage.currentMonthCost(),
                accountUsage.usagePercentage(),
                accountUsage.alertThreshold() == null ? 0 : accountUsage.alertThreshold(),
                month,
                currency,
                triggeredAt,
                buildAccountAlertMessage(accountUsage)
        );
    }

    private String buildMemberAlertMessage(BudgetUsageResponse usage) {
        return String.format(
                "이번 달 비용이 설정한 예산의 %.1f%%에 도달했습니다. (임계값 %d%%)",
                usage.usagePercentage(),
                usage.alertThreshold()
        );
    }

    private String buildAccountAlertMessage(BudgetUsageResponse.AccountBudgetUsage usage) {
        int threshold = usage.alertThreshold() == null ? 0 : usage.alertThreshold();
        return String.format(
                "%s (%s) 계정이 설정한 예산의 %.1f%%에 도달했습니다. (임계값 %d%%)",
                usage.accountName(),
                usage.provider(),
                usage.usagePercentage(),
                threshold
        );
    }

    private void resetMemberAlertIfNewMonth(Member member, String currentMonth) {
        if (member.getBudgetAlertTriggeredAt() == null) {
            return;
        }
        String triggeredMonth = member.getBudgetAlertTriggeredAt().format(MONTH_KEY);
        if (!triggeredMonth.equals(currentMonth)) {
            member.setBudgetAlertTriggeredAt(null);
            memberRepository.save(member);
        }
    }

    private void resetAccountAlertIfNewMonth(AccountBudgetSetting setting, String currentMonth) {
        if (setting.getAlertTriggeredAt() == null) {
            return;
        }
        String triggeredMonth = setting.getAlertTriggeredAt().format(MONTH_KEY);
        if (!triggeredMonth.equals(currentMonth)) {
            setting.setAlertTriggeredAt(null);
            accountBudgetSettingRepository.save(setting);
        }
    }

    private boolean alreadyTriggeredMemberThisMonth(Member member, String currentMonth) {
        if (member.getBudgetAlertTriggeredAt() == null) {
            return false;
        }
        String triggeredMonth = member.getBudgetAlertTriggeredAt().format(MONTH_KEY);
        return triggeredMonth.equals(currentMonth);
    }

    private boolean alreadyTriggeredAccountThisMonth(AccountBudgetSetting setting, String currentMonth) {
        if (setting.getAlertTriggeredAt() == null) {
            return false;
        }
        String triggeredMonth = setting.getAlertTriggeredAt().format(MONTH_KEY);
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

    private String safeAccountName(String candidate, CloudProvider provider, Long accountId) {
        if (candidate == null || candidate.isBlank()) {
            return provider.name() + " #" + accountId;
        }
        return candidate;
    }

    private record AccountKey(CloudProvider provider, Long accountId) {
    }

    private record UsageComputation(
            BudgetUsageResponse usage,
            CloudCostAggregator.CloudCostSnapshot snapshot,
            Map<AccountKey, AccountBudgetSetting> accountBudgets
    ) {
    }
}

