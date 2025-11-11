package com.budgetops.backend.simulator.repository;

import com.budgetops.backend.simulator.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    
    Optional<Proposal> findByProposalId(String proposalId);
    
    List<Proposal> findByStatus(Proposal.ProposalStatus status);
    
    List<Proposal> findByStatusAndExpiresAtAfter(
            Proposal.ProposalStatus status, 
            LocalDateTime now);
    
    List<Proposal> findByExpiresAtBefore(LocalDateTime now);
}

