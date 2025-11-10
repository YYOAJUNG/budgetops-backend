package com.budgetops.backend.billing.controller;

import com.budgetops.backend.billing.dto.response.BillingResponse;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.entity.Member;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.exception.MemberNotFoundException;
import com.budgetops.backend.billing.repository.MemberRepository;
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

        // 만료된 구독 확인 및 처리
        billingService.checkAndHandleExpiredSubscription(member);

        Billing billing = billingService.getBillingByMember(member)
                .orElseThrow(BillingNotFoundException::new);

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
        Billing billing = billingService.changePlan(member, planName);

        log.info("요금제 변경 완료: userId={}, newPlan={}, price={}",
                userId, billing.getCurrentPlan(), billing.getCurrentPrice());

        return ResponseEntity.ok(BillingResponse.from(billing));
    }

    /**
     * 구독 취소
     */
    @PostMapping("/cancel")
    public ResponseEntity<BillingResponse> cancelSubscription(@PathVariable Long userId) {
        Member member = getMemberById(userId);
        Billing billing = billingService.cancelSubscription(member);

        log.info("구독 취소 완료: userId={}, canceledAt={}", userId, billing.getCanceledAt());
        return ResponseEntity.ok(BillingResponse.from(billing));
    }
}
