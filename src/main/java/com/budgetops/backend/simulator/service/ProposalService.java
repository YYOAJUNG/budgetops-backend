package com.budgetops.backend.simulator.service;

import com.budgetops.backend.simulator.dto.ProposalRequest;
import com.budgetops.backend.simulator.dto.ProposalResponse;
import com.budgetops.backend.simulator.dto.SimulationResult;
import com.budgetops.backend.simulator.entity.Proposal;
import com.budgetops.backend.simulator.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 제안서 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalService {
    
    private final ProposalRepository proposalRepository;
    
    /**
     * 제안서 생성
     */
    @Transactional
    public ProposalResponse createProposal(ProposalRequest request) {
        String proposalId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(request.getTtlDays());
        
        Proposal proposal = new Proposal();
        proposal.setProposalId(proposalId);
        proposal.setStatus(Proposal.ProposalStatus.PENDING);
        proposal.setScenarioId(request.getScenarioId());
        proposal.setNote(request.getNote());
        proposal.setTtlDays(request.getTtlDays());
        proposal.setExpiresAt(expiresAt);
        
        // 시나리오 데이터는 나중에 조회 시 사용 (현재는 scenarioId만 저장)
        proposal.setScenarioData("{}");
        
        proposal = proposalRepository.save(proposal);
        
        log.info("Created proposal: proposalId={}, scenarioId={}, expiresAt={}", 
                proposalId, request.getScenarioId(), expiresAt);
        
        return ProposalResponse.builder()
                .proposalId(proposal.getProposalId())
                .status(proposal.getStatus().name())
                .createdAt(proposal.getCreatedAt())
                .expiresAt(proposal.getExpiresAt())
                .note(proposal.getNote())
                .build();
    }
    
    /**
     * 제안서 조회
     */
    public ProposalResponse getProposal(String proposalId) {
        Proposal proposal = proposalRepository.findByProposalId(proposalId)
                .orElseThrow(() -> new RuntimeException("제안서를 찾을 수 없습니다: " + proposalId));
        
        // 만료 확인
        if (proposal.getExpiresAt().isBefore(LocalDateTime.now()) && 
            proposal.getStatus() == Proposal.ProposalStatus.PENDING) {
            proposal.setStatus(Proposal.ProposalStatus.EXPIRED);
            proposal = proposalRepository.save(proposal);
        }
        
        return ProposalResponse.builder()
                .proposalId(proposal.getProposalId())
                .status(proposal.getStatus().name())
                .createdAt(proposal.getCreatedAt())
                .expiresAt(proposal.getExpiresAt())
                .note(proposal.getNote())
                .build();
    }
    
    /**
     * 제안서 승인
     */
    @Transactional
    public ProposalResponse approveProposal(String proposalId) {
        Proposal proposal = proposalRepository.findByProposalId(proposalId)
                .orElseThrow(() -> new RuntimeException("제안서를 찾을 수 없습니다: " + proposalId));
        
        if (proposal.getStatus() != Proposal.ProposalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 중인 제안서만 승인할 수 있습니다.");
        }
        
        proposal.setStatus(Proposal.ProposalStatus.APPROVED);
        proposal = proposalRepository.save(proposal);
        
        log.info("Approved proposal: proposalId={}", proposalId);
        
        return ProposalResponse.builder()
                .proposalId(proposal.getProposalId())
                .status(proposal.getStatus().name())
                .createdAt(proposal.getCreatedAt())
                .expiresAt(proposal.getExpiresAt())
                .note(proposal.getNote())
                .build();
    }
    
    /**
     * 제안서 거부
     */
    @Transactional
    public ProposalResponse rejectProposal(String proposalId) {
        Proposal proposal = proposalRepository.findByProposalId(proposalId)
                .orElseThrow(() -> new RuntimeException("제안서를 찾을 수 없습니다: " + proposalId));
        
        if (proposal.getStatus() != Proposal.ProposalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 중인 제안서만 거부할 수 있습니다.");
        }
        
        proposal.setStatus(Proposal.ProposalStatus.REJECTED);
        proposal = proposalRepository.save(proposal);
        
        log.info("Rejected proposal: proposalId={}", proposalId);
        
        return ProposalResponse.builder()
                .proposalId(proposal.getProposalId())
                .status(proposal.getStatus().name())
                .createdAt(proposal.getCreatedAt())
                .expiresAt(proposal.getExpiresAt())
                .note(proposal.getNote())
                .build();
    }
}

