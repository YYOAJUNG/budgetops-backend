package com.budgetops.backend.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BudgetSettingsResponse(
        BigDecimal monthlyBudgetLimit,
        Integer alertThreshold,
        LocalDateTime updatedAt,
        List<MemberAccountBudgetView> accountBudgets
) {

    public record MemberAccountBudgetView(
            String provider,
            Long accountId,
            BigDecimal monthlyBudgetLimit,
            Integer alertThreshold
    ) {
    }
}

