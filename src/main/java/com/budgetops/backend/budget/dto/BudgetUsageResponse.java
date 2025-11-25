package com.budgetops.backend.budget.dto;

import java.math.BigDecimal;

public record BudgetUsageResponse(
        BigDecimal monthlyBudgetLimit,
        Integer alertThreshold,
        BigDecimal currentMonthCost,
        double usagePercentage,
        boolean thresholdReached,
        String month,
        String currency
) {
}

