package com.budgetops.backend.billing.dto.response;

import com.budgetops.backend.billing.constants.DateConstants;
import com.budgetops.backend.billing.constants.TokenConstants;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.enums.BillingPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingResponse {
    // 기존 필드 (Backend 내부용)
    private Long id;
    private Long memberId;
    private BillingPlan currentPlan;
    private int currentPrice;
    private LocalDateTime nextBillingDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Frontend 호환 필드
    private String planId;              // "free", "pro", "enterprise" (소문자)
    private String planName;            // "Free", "Pro", "Enterprise"
    private Integer price;              // 월 가격 (Enterprise는 null)
    private String nextPaymentDate;     // ISO 8601 형식 (yyyy-MM-dd)
    private String status;              // "active", "canceled", "past_due"

    // 토큰/할당량 정보
    private Integer currentTokens;      // 현재 사용 가능한 토큰
    private Integer maxTokens;          // 최대 토큰 (월 할당량)
    private String tokenResetDate;      // 토큰 리셋 날짜 (yyyy-MM-dd)

    public static BillingResponse from(Billing billing) {
        BillingPlan plan = billing.getCurrentPlan();

        return BillingResponse.builder()
                // 기존 필드
                .id(billing.getId())
                .memberId(billing.getMember().getId())
                .currentPlan(plan)
                .currentPrice(billing.getCurrentPrice())
                .nextBillingDate(billing.getNextBillingDate())
                .createdAt(billing.getCreatedAt())
                .updatedAt(billing.getUpdatedAt())

                // Frontend 호환 필드
                .planId(plan.name().toLowerCase())
                .planName(plan.getDisplayName())
                .price(plan.getMonthlyPrice())
                .nextPaymentDate(billing.getNextBillingDate() != null
                    ? billing.getNextBillingDate().format(DateConstants.DATE_FORMAT)
                    : null)
                .status(billing.getStatus().getValue())  // "active", "canceled", "past_due"

                // 토큰/할당량 정보
                .currentTokens(billing.getCurrentTokens())
                .maxTokens(plan.isFree() ? TokenConstants.FREE_PLAN_MAX_TOKENS : TokenConstants.MAX_TOKEN_LIMIT)
                .tokenResetDate(billing.getNextBillingDate() != null
                    ? billing.getNextBillingDate().format(DateConstants.DATE_FORMAT)
                    : null)

                .build();
    }
}
