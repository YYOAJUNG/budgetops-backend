package com.budgetops.backend.budget.service;

import com.budgetops.backend.azure.service.AzureCostService;
import com.budgetops.backend.aws.service.AwsCostService;
import com.budgetops.backend.gcp.service.GcpCostService;
import com.budgetops.backend.ncp.service.NcpCostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

        BigDecimal aws = safeAwsSum(memberId, startDate, endDate);
        BigDecimal azure = safeAzureSum(memberId, startDate, endDate);
        BigDecimal gcp = safeGcpSum(memberId, startDate, endDate);
        BigDecimal ncp = safeNcpSum(memberId, month);

        BigDecimal total = aws.add(azure).add(gcp).add(ncp);

        return new CloudCostSnapshot(total, aws, azure, gcp, ncp, month);
    }

    private BigDecimal safeAwsSum(Long memberId, String startDate, String endDate) {
        try {
            List<AwsCostService.AccountCost> costs = awsCostService.getAllAccountsCosts(memberId, startDate, endDate);
            double sum = costs.stream().mapToDouble(AwsCostService.AccountCost::totalCost).sum();
            return currencyConversionService.usdToKrw(BigDecimal.valueOf(sum));
        } catch (Exception e) {
            log.warn("Failed to aggregate AWS cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeAzureSum(Long memberId, String startDate, String endDate) {
        try {
            List<AzureCostService.AccountCost> costs = azureCostService.getAllAccountsCosts(memberId, startDate, endDate);
            BigDecimal total = BigDecimal.ZERO;
            for (AzureCostService.AccountCost cost : costs) {
                BigDecimal amount = BigDecimal.valueOf(cost.getAmount()).setScale(2, RoundingMode.HALF_UP);
                String currency = cost.getCurrency() != null ? cost.getCurrency() : "USD";
                total = total.add(currencyConversionService.convert(amount, currency, "KRW"));
            }
            return total;
        } catch (Exception e) {
            log.warn("Failed to aggregate Azure cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeGcpSum(Long memberId, String startDate, String endDate) {
        try {
            double total = gcpCostService.getMemberTotalNetCost(memberId, startDate, endDate);
            return currencyConversionService.usdToKrw(BigDecimal.valueOf(total));
        } catch (Exception e) {
            log.warn("Failed to aggregate GCP cost for member {}: {}", memberId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal safeNcpSum(Long memberId, String month) {
        try {
            double total = ncpCostService.getMemberMonthlyCost(memberId, month);
            return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
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
            String month
    ) {
    }
}

