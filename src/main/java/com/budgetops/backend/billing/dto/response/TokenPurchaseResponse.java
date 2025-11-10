package com.budgetops.backend.billing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 토큰 구매 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenPurchaseResponse {
    private String transactionId;       // 거래 ID
    private Integer purchasedTokens;    // 구매한 토큰 수량
    private Integer bonusTokens;        // 보너스 토큰 (있는 경우)
    private Integer totalTokens;        // 총 추가된 토큰 (구매 + 보너스)
    private Integer currentTokens;      // 구매 후 현재 토큰 잔액
    private String purchaseDate;        // 구매 날짜 (yyyy-MM-dd HH:mm:ss)
}
