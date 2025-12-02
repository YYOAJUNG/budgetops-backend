package com.budgetops.backend.simulator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 시뮬레이션 제안서 엔티티
 */
@Entity
@Table(name = "simulation_proposal")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String proposalId;  // UUID

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProposalStatus status = ProposalStatus.PENDING;

    @Column(nullable = false)
    private String scenarioId;

    @Column(columnDefinition = "TEXT")
    private String scenarioData;  // JSON 형태로 SimulationResult 저장

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private Integer ttlDays;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ProposalStatus {
        PENDING, APPROVED, REJECTED, EXPIRED
    }

    public Proposal rejectProposal() {
        if (status != Proposal.ProposalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 중인 제안서만 거부할 수 있습니다.");
        }
        status = ProposalStatus.REJECTED;
        return this;
    }
}

