package com.budgetops.backend.budget.dto;

import com.budgetops.backend.budget.model.BudgetMode;
import com.budgetops.backend.budget.model.CloudProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BudgetSettingsResponse(
        BudgetMode mode,
        BigDecimal monthlyBudgetLimit,
        Integer alertThreshold,
        LocalDateTime updatedAt,
        List<AccountBudgetSettingResponse> accountBudgets
) {

    public record AccountBudgetSettingResponse(
            CloudProvider provider,
            Long accountId,
            String accountName,
            BigDecimal monthlyBudgetLimit,
            Integer alertThreshold
    ) {
    }
}

