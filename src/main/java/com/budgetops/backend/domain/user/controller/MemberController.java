package com.budgetops.backend.domain.user.controller;

import com.budgetops.backend.domain.user.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 관련 API (자기 계정 탈퇴 등)
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@Slf4j
public class MemberController {

    private final MemberService memberService;

    /**
     * 현재 로그인한 사용자의 회원 탈퇴
     * - 연결된 CSP 계정(AWS/Azure/GCP/NCP) 및 빌링/결제 정보까지 모두 정리 후 Member 삭제
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentMember() {
        Long memberId = getCurrentMemberId();
        log.info("Request to delete current member: {}", memberId);

        memberService.deleteMemberWithAssociations(memberId);

        // SecurityContext 정리 (선택적)
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}


