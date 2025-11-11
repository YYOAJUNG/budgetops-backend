package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;

/**
 * 제안서 응답 DTO
 */
@Value
@Builder
public class ProposalResponse {
    String proposalId;
    String status;  // PENDING, APPROVED, REJECTED, EXPIRED
    LocalDateTime createdAt;
    LocalDateTime expiresAt;
    SimulationResult scenario;
    String note;
}

