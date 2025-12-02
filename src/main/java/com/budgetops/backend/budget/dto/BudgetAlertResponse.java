package com.budgetops.backend.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BudgetAlertResponse(
        String mode,                 // CONSOLIDATED or ACCOUNT_SPECIFIC
        String provider,             // AWS / AZURE / GCP / NCP (계정별 예산 알림일 때만)
        Long accountId,              // 계정별 예산 알림 대상 계정 ID
        String accountName,          // 계정 이름 (선택)
        BigDecimal budgetLimit,      // 기준 예산 한도
        BigDecimal currentMonthCost, // 현재까지 비용
        double usagePercentage,      // 사용률 (%)
        int threshold,               // 임계값 (%)
        String month,                // YYYYMM
        String currency,             // KRW 등
        LocalDateTime triggeredAt,   // 알림 발생 시각
        String message               // 사용자에게 보여줄 메시지
) {
}

