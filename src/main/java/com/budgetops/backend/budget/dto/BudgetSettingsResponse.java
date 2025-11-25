package com.budgetops.backend.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BudgetSettingsResponse(
        BigDecimal monthlyBudgetLimit,
        Integer alertThreshold,
        LocalDateTime updatedAt
) {
}

