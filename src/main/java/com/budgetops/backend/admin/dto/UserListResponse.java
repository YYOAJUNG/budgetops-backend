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
public class UserListResponse {
    private Long id;
    private String email;
    private String name;
    private LocalDateTime createdAt;
    private String billingPlan;
    private Integer currentTokens;
    private Integer cloudAccountCount;
    private Integer awsAccountCount;
    private Integer azureAccountCount;
    private Integer gcpAccountCount;
    private Integer ncpAccountCount;
}

