package com.budgetops.backend.billing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결제 내역 응답 DTO
 * Frontend PaymentHistory 인터페이스와 호환
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistoryResponse {
    private String id;              // 결제 내역 ID (예: "INV-2024-10")
    private String date;            // 결제 날짜 (yyyy-MM-dd 형식)
    private Integer amount;         // 결제 금액
    private String status;          // "paid", "pending", "failed"
    private String invoiceUrl;      // 인보이스 URL
}
