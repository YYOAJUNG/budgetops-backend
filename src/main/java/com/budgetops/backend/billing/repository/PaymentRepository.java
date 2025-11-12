package com.budgetops.backend.billing.repository;

import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.domain.user.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByMember(Member member);

    Optional<Payment> findByImpUid(String impUid);

    boolean existsByMember(Member member);
}
