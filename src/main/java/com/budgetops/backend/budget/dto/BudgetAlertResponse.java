package com.budgetops.backend.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BudgetAlertResponse(
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

