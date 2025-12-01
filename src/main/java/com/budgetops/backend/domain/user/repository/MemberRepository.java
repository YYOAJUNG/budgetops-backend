package com.budgetops.backend.domain.user.repository;

import com.budgetops.backend.domain.user.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
}
