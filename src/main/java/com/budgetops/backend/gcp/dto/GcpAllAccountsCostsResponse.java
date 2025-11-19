package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 모든 GCP 계정의 비용 통합 조회 응답 DTO
 */
@Getter
@Setter
public class GcpAllAccountsCostsResponse {
    private Summary summary;
    private List<AccountCost> accounts;

    @Getter
    @Setter
    public static class Summary {
        private String currency;
        private double totalGrossCost;
        private double totalCreditUsed;
        private double totalNetCost;
    }

    @Getter
    @Setter
    public static class AccountCost {
        private Long accountId;
        private String accountName;
        private String currency;
        private double totalGrossCost;
        private double totalCreditUsed;
        private double totalNetCost;
    }
}

