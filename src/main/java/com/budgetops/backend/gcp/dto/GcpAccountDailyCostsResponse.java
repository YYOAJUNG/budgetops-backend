package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * GCP 계정별 일별 비용 정보 응답 DTO
 */
@Getter
@Setter
public class GcpAccountDailyCostsResponse {
    private Long accountId;
    private String accountName;
    private String currency;
    private double totalGrossCost;  // 프리티어/크레딧 공제 전 사용액
    private double totalCreditUsed; // 사용된 크레딧액
    private double totalNetCost;   // 실제 청구된 금액 (크레딧/프리티어 공제 후)
    private List<DailyCost> dailyCosts;

    @Getter
    @Setter
    public static class DailyCost {
        private String date;
        private double grossCost; // 프리티어/크레딧 공제 전 사용액
        private double creditUsed; // 사용된 크레딧액
        private double netCost;   // 실제 청구된 금액 (크레딧/프리티어 공제 후)
    }
}

