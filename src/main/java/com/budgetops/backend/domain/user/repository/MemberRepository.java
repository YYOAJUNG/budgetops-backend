package com.budgetops.backend.domain.user.repository;

import com.budgetops.backend.domain.user.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();

    /**
     * 이름 또는 이메일로 검색 (대소문자 무시)
     */
    Page<Member> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Pageable pageable);
}
