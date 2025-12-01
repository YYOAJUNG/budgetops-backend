package com.budgetops.backend.simulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/**
 * 제안서 생성 요청 DTO
 */
@Value
@Builder
public class ProposalRequest {
    @NotBlank(message = "시나리오 ID는 필수입니다")
    String scenarioId;
    
    String note;  // 메모
    
    @NotNull(message = "TTL 일수는 필수입니다")
    Integer ttlDays;  // 보관 기간
}

