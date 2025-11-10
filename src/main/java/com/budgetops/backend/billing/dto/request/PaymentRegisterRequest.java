package com.budgetops.backend.billing.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRegisterRequest {
    private String impUid;  // Iamport 거래 고유 번호
    private String customerUid;  // 빌링키 (customer_uid)
}
