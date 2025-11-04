package com.budgetops.backend.billing.dto.response;

import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long id;
    private Long memberId;
    private String impUid;
    private PaymentStatus status;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .memberId(payment.getMember().getId())
                .impUid(payment.getImpUid())
                .status(payment.getStatus())
                .lastVerifiedAt(payment.getLastVerifiedAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
