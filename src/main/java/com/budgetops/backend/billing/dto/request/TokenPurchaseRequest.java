package com.budgetops.backend.billing.dto.request;

import com.budgetops.backend.billing.enums.TokenPackage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 토큰 구매 요청 DTO
 * Frontend TokenPackage와 호환
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPurchaseRequest {
    private String packageId;       // "small", "medium", "large"
    private Integer amount;         // 구매할 토큰 수량 (검증용)
    private Integer price;          // 결제 금액 (검증용)
    private String impUid;          // 아임포트 결제 고유번호 (일반 결제 시)
    private Boolean useBillingKey;  // 빌링키 사용 여부 (true면 자동 결제)

    /**
     * 요청값이 패키지 정보와 일치하는지 검증
     * @throws IllegalArgumentException 불일치 시
     */
    public void validate() {
        TokenPackage tokenPackage = TokenPackage.fromId(packageId);

        if (tokenPackage.getTokenAmount() != amount) {
            throw new IllegalArgumentException(
                String.format("Token amount mismatch. Expected: %d, Actual: %d",
                    tokenPackage.getTokenAmount(), amount)
            );
        }

        if (tokenPackage.getPrice() != price) {
            throw new IllegalArgumentException(
                String.format("Price mismatch. Expected: %d, Actual: %d",
                    tokenPackage.getPrice(), price)
            );
        }
    }
}
