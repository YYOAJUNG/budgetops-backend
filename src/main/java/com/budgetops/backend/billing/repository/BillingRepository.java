package com.budgetops.backend.billing.repository;

import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingRepository extends JpaRepository<Billing, Long> {

    Optional<Billing> findByMember(Member member);

    Optional<Billing> findByMemberId(Long memberId);

    boolean existsByMember(Member member);
}
