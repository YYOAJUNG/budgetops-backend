package com.budgetops.backend.budget.dto;

import com.budgetops.backend.budget.model.BudgetMode;
import com.budgetops.backend.budget.model.CloudProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BudgetAlertResponse(
        BudgetMode mode,
        CloudProvider provider,
        Long accountId,
        String accountName,
        BigDecimal budgetLimit,
        BigDecimal currentMonthCost,
        double usagePercentage,
        int threshold,
        String month,
        String currency,
        LocalDateTime triggeredAt,
        String message
) {
}

