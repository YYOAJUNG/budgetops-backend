package com.budgetops.backend.simulator.service;

import com.budgetops.backend.simulator.entity.Proposal;
import com.budgetops.backend.simulator.repository.ProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProposalServiceTest {
    private ProposalRepository proposalRepository;
    private ProposalService proposalService;

    @BeforeEach
    void setUp() {
        proposalRepository = mock(ProposalRepository.class);
        proposalService = new ProposalService(proposalRepository);
    }

    @Test
    void rejectProposalRuntimeExceptionTest() {
        // given
        final var proposalId = "proposalId";
        when(proposalRepository.findByProposalId(proposalId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(RuntimeException.class, () -> proposalService.rejectProposal(proposalId));
    }

    @Test
    void rejectProposalTest() {
        // given
        final var proposalId = "proposalId";
        final var givenProposal = new Proposal();
        givenProposal.setProposalId(proposalId);
        givenProposal.setStatus(Proposal.ProposalStatus.PENDING);

        when(proposalRepository.findByProposalId(proposalId)).thenReturn(Optional.of(givenProposal));
        when(proposalRepository.save(givenProposal)).thenReturn(givenProposal);

        // when
        final var response = proposalService.rejectProposal(proposalId);

        // then
        assertThat(response.getProposalId()).isEqualTo(proposalId);
        assertThat(response.getStatus()).isEqualTo(Proposal.ProposalStatus.REJECTED.name());
        assertThat(response.getCreatedAt()).isEqualTo(givenProposal.getCreatedAt());
        assertThat(response.getExpiresAt()).isEqualTo(givenProposal.getExpiresAt());
        assertThat(response.getNote()).isEqualTo(givenProposal.getNote());
    }
}