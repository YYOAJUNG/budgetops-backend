package com.budgetops.backend.billing.repository;

import com.budgetops.backend.billing.entity.PaymentHistory;
import com.budgetops.backend.domain.user.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    /**
     * 회원의 모든 결제 내역 조회 (최신순)
     */
    List<PaymentHistory> findByMemberOrderByCreatedAtDesc(Member member);

    /**
     * 회원 ID로 모든 결제 내역 조회 (최신순)
     */
    List<PaymentHistory> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    /**
     * impUid로 결제 내역 조회
     */
    PaymentHistory findByImpUid(String impUid);

    /**
     * merchantUid로 결제 내역 조회
     */
    PaymentHistory findByMerchantUid(String merchantUid);

    /**
     * 사용자 이름 또는 이메일로 결제 내역 검색
     */
    @Query("SELECT ph FROM PaymentHistory ph JOIN ph.member m WHERE m.name LIKE CONCAT('%', :search, '%') OR m.email LIKE CONCAT('%', :search, '%')")
    List<PaymentHistory> findByMemberNameOrEmailContaining(@Param("search") String search);
}
