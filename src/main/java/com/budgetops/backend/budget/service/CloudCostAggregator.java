package com.budgetops.backend.budget.service;

import com.budgetops.backend.azure.service.AzureCostService;
import com.budgetops.backend.aws.service.AwsCostService;
import com.budgetops.backend.gcp.dto.GcpAllAccountsCostsResponse;
import com.budgetops.backend.gcp.service.GcpCostService;
import com.budgetops.backend.ncp.service.NcpCostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudCostAggregator {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final AwsCostService awsCostService;
    private final AzureCostService azureCostService;
    private final GcpCostService gcpCostService;
    private final NcpCostService ncpCostService;
    private final CurrencyConversionService currencyConversionService;

    public CloudCostSnapshot calculateCurrentMonth(Long memberId) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate endExclusive = start.plusMonths(1);

        String startDate = start.toString();
        String endDate = endExclusive.toString();
        String month = start.format(MONTH_KEY);

        List<AccountCostSnapshot> accountCosts = new ArrayList<>();

        BigDecimal aws = safeAwsSum(memberId, startDate, endDate, accountCosts);
        BigDecimal azure = safeAzureSum(memberId, startDate, endDate, accountCosts);
        BigDecimal gcp = safeGcpSum(memberId, startDate, endDate, accountCosts);
        BigDecimal ncp = safeNcpSum(memberId, month, accountCosts);

        BigDecimal total = aws.add(azure).add(gcp).add(ncp);

        return new CloudCostSnapshot(total, aws, azure, gcp, ncp, month, accountCosts);
    }

    private BigDecimal safeAwsSum(Long memberId, String startDate, String endDate, List<AccountCostSnapshot> accountCosts) {
        try {
            List<AwsCostService.AccountCost> costs = awsCostService.getAllAccountsCosts(memberId, startDate, endDate);
            BigDecimal total = BigDecimal.ZERO;
            for (AwsCostService.AccountCost cost : costs) {
                BigDecimal usd = BigDecimal.valueOf(cost.totalCost());
                BigDecimal krw = currencyConversionService.usdToKrw(usd);
                total = total.add(krw);
                accountCosts.add(new AccountCostSnapshot(
                        "AWS",
                        cost.accountId(),
                        cost.accountName(),
                        krw
                ));
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Failed to aggregate AWS cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeAzureSum(Long memberId, String startDate, String endDate, List<AccountCostSnapshot> accountCosts) {
        try {
            List<AzureCostService.AccountCost> costs = azureCostService.getAllAccountsCosts(memberId, startDate, endDate);
            BigDecimal total = BigDecimal.ZERO;
            for (AzureCostService.AccountCost cost : costs) {
                BigDecimal amount = BigDecimal.valueOf(cost.getAmount()).setScale(2, RoundingMode.HALF_UP);
                String currency = cost.getCurrency() != null ? cost.getCurrency() : "USD";
                BigDecimal krw = currencyConversionService.convert(amount, currency, "KRW");
                total = total.add(krw);
                accountCosts.add(new AccountCostSnapshot(
                        "AZURE",
                        cost.getAccountId(),
                        cost.getAccountName(),
                        krw
                ));
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Failed to aggregate Azure cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeGcpSum(Long memberId, String startDate, String endDate, List<AccountCostSnapshot> accountCosts) {
        try {
            // 계정별 비용 정보를 가져와서 합산
            GcpAllAccountsCostsResponse allCosts = gcpCostService.getMemberAccountsCosts(memberId, startDate, endDate);
            BigDecimal total = BigDecimal.ZERO;
            if (allCosts.getAccounts() != null) {
                for (GcpAllAccountsCostsResponse.AccountCost accountCost : allCosts.getAccounts()) {
                    BigDecimal netUsd = BigDecimal.valueOf(accountCost.getTotalNetCost());
                    BigDecimal krw = currencyConversionService.usdToKrw(netUsd);
                    total = total.add(krw);
                    accountCosts.add(new AccountCostSnapshot(
                            "GCP",
                            accountCost.getAccountId(),
                            accountCost.getAccountName(),
                            krw
                    ));
                }
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Failed to aggregate GCP cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeNcpSum(Long memberId, String month, List<AccountCostSnapshot> accountCosts) {
        try {
            List<NcpCostService.AccountMonthlyCost> costs = ncpCostService.getMemberAccountsMonthlyCost(memberId, month);
            BigDecimal total = BigDecimal.ZERO;
            for (NcpCostService.AccountMonthlyCost cost : costs) {
                BigDecimal krw = BigDecimal.valueOf(cost.totalCost()).setScale(2, RoundingMode.HALF_UP);
                total = total.add(krw);
                accountCosts.add(new AccountCostSnapshot(
                        "NCP",
                        cost.accountId(),
                        cost.accountName(),
                        krw
                ));
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to aggregate NCP cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public record CloudCostSnapshot(
            BigDecimal totalKrw,
            BigDecimal awsKrw,
            BigDecimal azureKrw,
            BigDecimal gcpKrw,
            BigDecimal ncpKrw,
            String month,
            List<AccountCostSnapshot> accountCosts
    ) {
    }

    public record AccountCostSnapshot(
            String provider,
            Long accountId,
            String accountName,
            BigDecimal costKrw
    ) {
    }
}

