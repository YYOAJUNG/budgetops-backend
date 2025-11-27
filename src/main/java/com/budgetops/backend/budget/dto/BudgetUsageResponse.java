package com.budgetops.backend.budget.dto;

import com.budgetops.backend.budget.model.BudgetMode;
import com.budgetops.backend.budget.model.CloudProvider;

import java.math.BigDecimal;
import java.util.List;

public record BudgetUsageResponse(
        BudgetMode mode,
        BigDecimal monthlyBudgetLimit,
        Integer alertThreshold,
        BigDecimal currentMonthCost,
        double usagePercentage,
        boolean thresholdReached,
        String month,
        String currency,
        List<AccountBudgetUsage> accountUsages
) {

    public record AccountBudgetUsage(
            CloudProvider provider,
            Long accountId,
            String accountName,
            BigDecimal currentMonthCost,
            BigDecimal monthlyBudgetLimit,
            Integer alertThreshold,
            double usagePercentage,
            boolean thresholdReached,
            boolean hasBudget
    ) {
    }
}

