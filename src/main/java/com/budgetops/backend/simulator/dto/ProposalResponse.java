package com.budgetops.backend.simulator.dto;

import com.budgetops.backend.simulator.entity.Proposal;
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

    public static ProposalResponse fromReject(Proposal proposal) {
        return ProposalResponse.builder()
                .proposalId(proposal.getProposalId())
                .status(proposal.getStatus().name())
                .createdAt(proposal.getCreatedAt())
                .expiresAt(proposal.getExpiresAt())
                .note(proposal.getNote())
                .build();
    }
}

