package com.budgetops.backend.simulator.service;

import com.budgetops.backend.simulator.dto.ProposalRequest;
import com.budgetops.backend.simulator.dto.ProposalResponse;
import com.budgetops.backend.simulator.entity.Proposal;
import com.budgetops.backend.simulator.repository.ProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Proposal Service 테스트")
class ProposalServiceTest {

    @Mock
    private ProposalRepository proposalRepository;

    @InjectMocks
    private ProposalService proposalService;

    private Proposal testProposal;
    private ProposalRequest testRequest;

    @BeforeEach
    void setUp() {
        testProposal = new Proposal();
        testProposal.setId(1L);
        testProposal.setProposalId("test-proposal-id");
        testProposal.setStatus(Proposal.ProposalStatus.PENDING);
        testProposal.setScenarioId("scenario-123");
        testProposal.setNote("테스트 제안서");
        testProposal.setTtlDays(30);
        testProposal.setExpiresAt(LocalDateTime.now().plusDays(30));
        testProposal.setCreatedAt(LocalDateTime.now());
        testProposal.setUpdatedAt(LocalDateTime.now());

        testRequest = ProposalRequest.builder()
                .scenarioId("scenario-123")
                .note("테스트 제안서")
                .ttlDays(30)
                .build();
    }

    @Test
    @DisplayName("제안서 생성 성공")
    void createProposal_Success() {
        // given
        given(proposalRepository.save(any(Proposal.class))).willReturn(testProposal);

        // when
        ProposalResponse response = proposalService.createProposal(testRequest);

        // then
        assertThat(response.getProposalId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getNote()).isEqualTo("테스트 제안서");
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    @DisplayName("제안서 조회 성공")
    void getProposal_Success() {
        // given
        given(proposalRepository.findByProposalId("test-proposal-id"))
                .willReturn(Optional.of(testProposal));

        // when
        ProposalResponse response = proposalService.getProposal("test-proposal-id");

        // then
        assertThat(response.getProposalId()).isEqualTo("test-proposal-id");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getNote()).isEqualTo("테스트 제안서");
    }

    @Test
    @DisplayName("제안서 조회 - 존재하지 않는 제안서")
    void getProposal_NotFound() {
        // given
        given(proposalRepository.findByProposalId("non-existent"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> proposalService.getProposal("non-existent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("제안서를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("제안서 조회 - 만료된 제안서 자동 상태 변경")
    void getProposal_ExpiredAutoUpdate() {
        // given
        testProposal.setExpiresAt(LocalDateTime.now().minusDays(1));
        given(proposalRepository.findByProposalId("test-proposal-id"))
                .willReturn(Optional.of(testProposal));
        given(proposalRepository.save(any(Proposal.class))).willReturn(testProposal);

        // when
        ProposalResponse response = proposalService.getProposal("test-proposal-id");

        // then
        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    @DisplayName("제안서 승인 성공")
    void approveProposal_Success() {
        // given
        testProposal.setStatus(Proposal.ProposalStatus.PENDING);
        given(proposalRepository.findByProposalId("test-proposal-id"))
                .willReturn(Optional.of(testProposal));
        
        Proposal approvedProposal = new Proposal();
        approvedProposal.setId(1L);
        approvedProposal.setProposalId("test-proposal-id");
        approvedProposal.setStatus(Proposal.ProposalStatus.APPROVED);
        approvedProposal.setScenarioId("scenario-123");
        approvedProposal.setNote("테스트 제안서");
        approvedProposal.setTtlDays(30);
        approvedProposal.setExpiresAt(LocalDateTime.now().plusDays(30));
        approvedProposal.setCreatedAt(LocalDateTime.now());
        approvedProposal.setUpdatedAt(LocalDateTime.now());
        
        given(proposalRepository.save(any(Proposal.class))).willReturn(approvedProposal);

        // when
        ProposalResponse response = proposalService.approveProposal("test-proposal-id");

        // then
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    @DisplayName("제안서 승인 - PENDING이 아닌 상태")
    void approveProposal_NotPending() {
        // given
        testProposal.setStatus(Proposal.ProposalStatus.APPROVED);
        given(proposalRepository.findByProposalId("test-proposal-id"))
                .willReturn(Optional.of(testProposal));

        // when & then
        assertThatThrownBy(() -> proposalService.approveProposal("test-proposal-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("승인 대기 중인 제안서만 승인할 수 있습니다");
    }

    @Test
    @DisplayName("제안서 거부 성공")
    void rejectProposal_Success() {
        // given
        testProposal.setStatus(Proposal.ProposalStatus.PENDING);
        given(proposalRepository.findByProposalId("test-proposal-id"))
                .willReturn(Optional.of(testProposal));
        
        Proposal rejectedProposal = new Proposal();
        rejectedProposal.setId(1L);
        rejectedProposal.setProposalId("test-proposal-id");
        rejectedProposal.setStatus(Proposal.ProposalStatus.REJECTED);
        rejectedProposal.setScenarioId("scenario-123");
        rejectedProposal.setNote("테스트 제안서");
        rejectedProposal.setTtlDays(30);
        rejectedProposal.setExpiresAt(LocalDateTime.now().plusDays(30));
        rejectedProposal.setCreatedAt(LocalDateTime.now());
        rejectedProposal.setUpdatedAt(LocalDateTime.now());
        
        given(proposalRepository.save(any(Proposal.class))).willReturn(rejectedProposal);

        // when
        ProposalResponse response = proposalService.rejectProposal("test-proposal-id");

        // then
        assertThat(response.getStatus()).isEqualTo("REJECTED");
        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    @DisplayName("제안서 거부 - PENDING이 아닌 상태")
    void rejectProposal_NotPending() {
        // given
        testProposal.setStatus(Proposal.ProposalStatus.REJECTED);
        given(proposalRepository.findByProposalId("test-proposal-id"))
                .willReturn(Optional.of(testProposal));

        // when & then
        assertThatThrownBy(() -> proposalService.rejectProposal("test-proposal-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("승인 대기 중인 제안서만 거부할 수 있습니다");
    }

    @Test
    @DisplayName("제안서 조회 - 이미 만료된 제안서는 상태 변경하지 않음")
    void getProposal_AlreadyExpired() {
        // given
        testProposal.setStatus(Proposal.ProposalStatus.EXPIRED);
        testProposal.setExpiresAt(LocalDateTime.now().minusDays(1));
        given(proposalRepository.findByProposalId("test-proposal-id"))
                .willReturn(Optional.of(testProposal));

        // when
        ProposalResponse response = proposalService.getProposal("test-proposal-id");

        // then
        assertThat(response.getStatus()).isEqualTo("EXPIRED");
        // 이미 EXPIRED 상태이므로 save가 호출되지 않음
    }
}

