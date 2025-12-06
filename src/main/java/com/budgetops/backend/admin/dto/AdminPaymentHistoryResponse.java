package com.budgetops.backend.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentHistoryResponse {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private String paymentType; // "MEMBERSHIP" 또는 "TOKEN_PURCHASE"
    private String impUid;
    private Integer amount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastVerifiedAt;
}

