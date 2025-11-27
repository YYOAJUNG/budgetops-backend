package com.budgetops.backend.budget.service;

import com.budgetops.backend.aws.service.AwsCostService;
import com.budgetops.backend.azure.service.AzureCostService;
import com.budgetops.backend.budget.model.CloudProvider;
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

        BigDecimal aws = accumulateAwsCosts(memberId, startDate, endDate, accountCosts);
        BigDecimal azure = accumulateAzureCosts(memberId, startDate, endDate, accountCosts);
        BigDecimal gcp = accumulateGcpCosts(memberId, startDate, endDate, accountCosts);
        BigDecimal ncp = accumulateNcpCosts(memberId, month, accountCosts);

        BigDecimal total = aws.add(azure).add(gcp).add(ncp);

        return new CloudCostSnapshot(total, aws, azure, gcp, ncp, month, List.copyOf(accountCosts));
    }

    private BigDecimal accumulateAwsCosts(
            Long memberId,
            String startDate,
            String endDate,
            List<AccountCostSnapshot> accountCosts
    ) {
        try {
            List<AwsCostService.AccountCost> costs = awsCostService.getAllAccountsCosts(memberId, startDate, endDate);
            BigDecimal total = BigDecimal.ZERO;
            for (AwsCostService.AccountCost cost : costs) {
                BigDecimal amountUsd = BigDecimal.valueOf(cost.totalCost()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal amountKrw = currencyConversionService.usdToKrw(amountUsd);
                total = total.add(amountKrw);
                accountCosts.add(new AccountCostSnapshot(
                        CloudProvider.AWS,
                        cost.accountId(),
                        cost.accountName(),
                        amountKrw,
                        "USD",
                        amountUsd
                ));
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to aggregate AWS cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal accumulateAzureCosts(
            Long memberId,
            String startDate,
            String endDate,
            List<AccountCostSnapshot> accountCosts
    ) {
        try {
            List<AzureCostService.AccountCost> costs = azureCostService.getAllAccountsCosts(memberId, startDate, endDate);
            BigDecimal total = BigDecimal.ZERO;
            for (AzureCostService.AccountCost cost : costs) {
                String currency = cost.getCurrency() != null ? cost.getCurrency() : "USD";
                BigDecimal amount = BigDecimal.valueOf(cost.getAmount()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal amountKrw = convertToKrw(amount, currency);
                total = total.add(amountKrw);
                accountCosts.add(new AccountCostSnapshot(
                        CloudProvider.AZURE,
                        cost.getAccountId(),
                        cost.getAccountName(),
                        amountKrw,
                        currency,
                        amount
                ));
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to aggregate Azure cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal accumulateGcpCosts(
            Long memberId,
            String startDate,
            String endDate,
            List<AccountCostSnapshot> accountCosts
    ) {
        try {
            List<GcpCostService.AccountCost> costs = gcpCostService.getMemberAccountCosts(memberId, startDate, endDate);
            BigDecimal total = BigDecimal.ZERO;
            for (GcpCostService.AccountCost cost : costs) {
                String currency = cost.currency() != null ? cost.currency() : "USD";
                BigDecimal amount = BigDecimal.valueOf(cost.totalNetCost()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal amountKrw = convertToKrw(amount, currency);
                total = total.add(amountKrw);
                accountCosts.add(new AccountCostSnapshot(
                        CloudProvider.GCP,
                        cost.accountId(),
                        cost.accountName(),
                        amountKrw,
                        currency,
                        amount
                ));
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to aggregate GCP cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal accumulateNcpCosts(
            Long memberId,
            String month,
            List<AccountCostSnapshot> accountCosts
    ) {
        try {
            List<NcpCostService.AccountCost> costs = ncpCostService.getMemberAccountCosts(memberId, month);
            BigDecimal total = BigDecimal.ZERO;
            for (NcpCostService.AccountCost cost : costs) {
                BigDecimal amount = BigDecimal.valueOf(cost.totalCost()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal amountKrw = convertToKrw(amount, cost.currency());
                total = total.add(amountKrw);
                accountCosts.add(new AccountCostSnapshot(
                        CloudProvider.NCP,
                        cost.accountId(),
                        cost.accountName(),
                        amountKrw,
                        cost.currency(),
                        amount
                ));
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to aggregate NCP cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal convertToKrw(BigDecimal amount, String currency) {
        if (currency == null || currency.equalsIgnoreCase("KRW")) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        if (currency.equalsIgnoreCase("USD")) {
            return currencyConversionService.usdToKrw(amount);
        }
        return currencyConversionService.convert(amount, currency, "KRW");
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
            CloudProvider provider,
            Long accountId,
            String accountName,
            BigDecimal costKrw,
            String originalCurrency,
            BigDecimal originalAmount
    ) {
    }
}

