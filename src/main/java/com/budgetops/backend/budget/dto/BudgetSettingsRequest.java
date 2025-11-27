package com.budgetops.backend.budget.dto;

import com.budgetops.backend.budget.model.BudgetMode;
import com.budgetops.backend.budget.model.CloudProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record BudgetSettingsRequest(
        @NotNull(message = "예산 모드를 선택해주세요.")
        BudgetMode mode,

        @NotNull(message = "예산 한도를 입력해주세요.")
        @DecimalMin(value = "0.0", inclusive = true, message = "예산 한도는 0 이상이어야 합니다.")
        @Digits(integer = 15, fraction = 2, message = "예산 한도는 소수점 둘째 자리까지 입력 가능합니다.")
        BigDecimal monthlyBudgetLimit,

        @NotNull(message = "알림 임계값을 선택해주세요.")
        @Min(value = 0, message = "알림 임계값은 0 이상이어야 합니다.")
        @Max(value = 100, message = "알림 임계값은 100 이하이어야 합니다.")
        Integer alertThreshold,

        @Valid
        List<AccountBudgetSettingRequest> accountBudgets
) {

    public record AccountBudgetSettingRequest(
            @NotNull(message = "클라우드 제공자를 선택해주세요.")
            CloudProvider provider,

            @NotNull(message = "계정을 선택해주세요.")
            Long accountId,

            @NotNull(message = "계정별 예산 한도를 입력해주세요.")
            @DecimalMin(value = "0.0", inclusive = true, message = "계정별 예산 한도는 0 이상이어야 합니다.")
            @Digits(integer = 15, fraction = 2, message = "계정별 예산 한도는 소수점 둘째 자리까지 입력 가능합니다.")
            BigDecimal monthlyBudgetLimit,

            @Min(value = 0, message = "알림 임계값은 0 이상이어야 합니다.")
            @Max(value = 100, message = "알림 임계값은 100 이하이어야 합니다.")
            Integer alertThreshold
    ) {
    }
}

