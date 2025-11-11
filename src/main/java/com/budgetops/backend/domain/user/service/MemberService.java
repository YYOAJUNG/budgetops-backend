package com.budgetops.backend.domain.user.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    /**
     * OAuth 로그인 시 Member upsert
     * - 기존 회원: name 업데이트
     * - 신규 회원: insert
     */
    @Transactional
    public Member upsertOAuthMember(String email, String name) {
        return memberRepository.findByEmail(email)
                .map(existingMember -> {
                    // 기존 회원 - name 업데이트
                    log.info("Existing member login: email={}, id={}", email, existingMember.getId());
                    existingMember.setName(name);
                    return memberRepository.save(existingMember);
                })
                .orElseGet(() -> {
                    // 신규 회원 - insert
                    Member newMember = Member.builder()
                            .email(email)
                            .name(name)
                            .build();
                    Member savedMember = memberRepository.save(newMember);
                    log.info("New member created: email={}, id={}", email, savedMember.getId());
                    return savedMember;
                });
    }
}
