package com.budgetops.backend.billing.controller;

import com.budgetops.backend.billing.dto.response.BillingResponse;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.exception.MemberNotFoundException;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{userId}/billing")
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final BillingService billingService;
    private final MemberRepository memberRepository;

    private Member getMemberById(Long userId) {
        return memberRepository.findById(userId)
                .orElseThrow(() -> new MemberNotFoundException(userId));
    }

    /**
     * 요금제 정보 조회
     */
    @GetMapping
    public ResponseEntity<BillingResponse> getBilling(@PathVariable Long userId) {
        Member member = getMemberById(userId);

        // Billing 데이터가 없으면 자동으로 초기화
        Billing billing = billingService.getBillingByMember(member)
                .orElseGet(() -> {
                    log.warn("Billing not found for user {}, initializing...", userId);
                    return billingService.initializeBilling(member);
                });

        log.info("요금제 조회: userId={}, plan={}", userId, billing.getCurrentPlan());
        return ResponseEntity.ok(BillingResponse.from(billing));
    }

    /**
     * 요금제 변경
     */
    @PutMapping("/plan/{planName}")
    public ResponseEntity<BillingResponse> changePlan(
            @PathVariable Long userId,
            @PathVariable String planName
    ) {
        Member member = getMemberById(userId);

        // Billing 데이터가 없으면 먼저 초기화
        billingService.getBillingByMember(member)
                .orElseGet(() -> {
                    log.warn("Billing not found for user {}, initializing before plan change...", userId);
                    return billingService.initializeBilling(member);
                });

        Billing billing = billingService.changePlan(member, planName);

        log.info("요금제 변경 완료: userId={}, newPlan={}, price={}",
                userId, billing.getCurrentPlan(), billing.getCurrentPrice());

        return ResponseEntity.ok(BillingResponse.from(billing));
    }

    /**
     * 구독 취소 (다음 결제일까지 현재 플랜 유지)
     */
    @PostMapping("/cancel")
    public ResponseEntity<BillingResponse> cancelSubscription(@PathVariable Long userId) {
        Member member = getMemberById(userId);
        Billing billing = billingService.cancelSubscription(member);

        log.info("구독 취소 완료: userId={}, plan={}, nextBillingDate={}",
                userId, billing.getCurrentPlan(), billing.getNextBillingDate());

        return ResponseEntity.ok(BillingResponse.from(billing));
    }
}
