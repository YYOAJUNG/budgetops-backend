package com.budgetops.backend.budget.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetUsageResponse(
        BigDecimal monthlyBudgetLimit,
        Integer alertThreshold,
        BigDecimal currentMonthCost,
        double usagePercentage,
        boolean thresholdReached,
        String month,
        String currency,
        List<AccountUsage> accountUsages
) {

    public record AccountUsage(
            String provider,
            Long accountId,
            String accountName,
            BigDecimal currentMonthCost,
            BigDecimal monthlyBudgetLimit,
            Integer alertThreshold,
            double usagePercentage,
            boolean thresholdReached,
            String currency
    ) {
    }
}

