package com.budgetops.backend.domain.user.repository;

import com.budgetops.backend.domain.user.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 전체 조회 (최근 접속일 기준 정렬: null이 아닌 것 우선, 최신순)
     * null인 것들은 가입일 기준 최신순
     */
    @Query("SELECT m FROM Member m ORDER BY CASE WHEN m.lastLoginAt IS NULL THEN 1 ELSE 0 END, m.lastLoginAt DESC NULLS LAST, m.createdAt DESC")
    Page<Member> findAllOrderByLastLoginAtDesc(Pageable pageable);

    /**
     * 이름 또는 이메일로 검색 (최근 접속일 기준 정렬)
     */
    @Query("SELECT m FROM Member m WHERE LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(m.email) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY CASE WHEN m.lastLoginAt IS NULL THEN 1 ELSE 0 END, m.lastLoginAt DESC NULLS LAST, m.createdAt DESC")
    Page<Member> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByLastLoginAtDesc(@Param("search") String search, Pageable pageable);
}
