package com.budgetops.backend.billing.repository;

import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.domain.user.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByMember(Member member);

    Optional<Payment> findByImpUid(String impUid);

    boolean existsByMember(Member member);

    /**
     * 사용자 이름 또는 이메일로 결제 내역 검색
     */
    @Query("SELECT p FROM Payment p JOIN p.member m WHERE m.name LIKE %:search% OR m.email LIKE %:search%")
    List<Payment> findByMemberNameOrEmailContaining(@Param("search") String search);
}
