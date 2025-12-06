package com.budgetops.backend.billing.service;

import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.billing.enums.BillingPlan;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.exception.InvalidBillingPlanException;
import com.budgetops.backend.billing.exception.PaymentRequiredException;
import com.budgetops.backend.billing.repository.BillingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class BillingService {

    private final BillingRepository billingRepository;
    private final PaymentService paymentService;

    /**
     * 사용자 가입 시 기본 요금제(FREE) 초기화
     */
    public Billing initializeBilling(Member member) {
        Billing billing = Billing.builder()
                .member(member)
                .currentPlan(BillingPlan.FREE)
                .currentPrice(0)
                .currentTokens(BillingPlan.FREE.getAiAssistantQuota())  // Free 플랜 초기 토큰 (10k)
                .build();

        billing.setNextBillingDate(null);

        log.info("빌링 초기화: memberId={}, plan=FREE, initialTokens={}",
                member.getId(), BillingPlan.FREE.getAiAssistantQuota());
        return billingRepository.save(billing);
    }

    /**
     * 사용자의 요금제 조회
     */
    @Transactional(readOnly = true)
    public Optional<Billing> getBillingByMember(Member member) {
        return billingRepository.findByMember(member);
    }

    /**
     * 요금제 변경 (planName을 String으로 받아서 변환)
     */
    public Billing changePlan(Member member, String planName) {
        BillingPlan newPlan;
        try {
            newPlan = BillingPlan.valueOf(planName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidBillingPlanException(planName, e);
        }

        return changePlan(member, newPlan);
    }

    /**
     * 요금제 변경 (BillingPlan enum으로 받음)
     */
    public Billing changePlan(Member member, BillingPlan newPlan) {
        Billing billing = billingRepository.findByMember(member)
                .orElseThrow(() -> new BillingNotFoundException(member.getId()));

        BillingPlan currentPlan = billing.getCurrentPlan();

        // PRO 요금제로 변경 시 결제 처리
        if (!newPlan.isFree()) {
            boolean isPaymentRegistered = paymentService.isPaymentRegistered(member);
            if (!isPaymentRegistered) {
                throw new PaymentRequiredException();
            }

            // FREE → PRO로 변경 시 빌링키로 즉시 결제
            if (currentPlan.isFree()) {
                String merchantUid = "PLAN_" + System.currentTimeMillis();
                String orderName = String.format("%s 플랜 결제", newPlan.getDisplayName());
                int amount = newPlan.getMonthlyPrice();

                // 빌링키로 즉시 결제
                String impUid = paymentService.payWithBillingKey(member, merchantUid, orderName, amount);
                log.info("플랜 변경 결제 완료: memberId={}, plan={}, impUid={}, amount={}",
                        member.getId(), newPlan, impUid, amount);
            }
        }

        // 요금제 변경
        billing.changePlan(newPlan);

        // 플랜에 따라 토큰 조정
        int currentTokens = billing.getCurrentTokens();
        int planQuota = newPlan.getAiAssistantQuota();

        // 현재 토큰이 새 플랜의 기본 할당량보다 적으면 기본 할당량으로 설정
        // (처음 가입 or 플랜 업그레이드 시 최소 보장)
        if (currentTokens < planQuota) {
            billing.setCurrentTokens(planQuota);
            log.info("플랜 변경에 따른 토큰 증가: memberId={}, oldPlan={}, newPlan={}, oldTokens={}, newTokens={}",
                    member.getId(), currentPlan, newPlan, currentTokens, planQuota);
        } else {
            // 기존 토큰 유지 (사용자가 구매한 토큰 보호)
            log.info("플랜 변경, 기존 토큰 유지: memberId={}, oldPlan={}, newPlan={}, tokens={}",
                    member.getId(), currentPlan, newPlan, currentTokens);
        }

        // 다음 결제일 업데이트 (유료 플랜인 경우에만)
        if (!newPlan.isFree()) {
            billing.setNextBillingDateFromNow();
            log.info("다음 결제일 설정: memberId={}, nextBillingDate={}",
                    member.getId(), billing.getNextBillingDate());
        } else {
            // FREE 플랜으로 변경 시 결제일 초기화
            billing.setNextBillingDate(null);
            log.info("FREE 플랜으로 변경, 결제일 초기화: memberId={}", member.getId());
        }

        log.info("요금제 변경: memberId={}, newPlan={}, price={}",
                member.getId(), newPlan, billing.getCurrentPrice());

        return billingRepository.save(billing);
    }

    /**
     * 빌링 정보 저장
     */
    public Billing saveBilling(Billing billing) {
        return billingRepository.save(billing);
    }

    /**
     * 빌링 정보 삭제
     */
    public void deleteBilling(Member member) {
        Optional<Billing> billing = billingRepository.findByMember(member);
        billing.ifPresent(b -> {
            billingRepository.delete(b);
            log.info("빌링 정보 삭제: memberId={}", member.getId());
        });
    }

    /**
     * 토큰 차감 (AI 사용 시)
     */
    public int consumeTokens(Long memberId, int tokens) {
        Billing billing = billingRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BillingNotFoundException(memberId));

        if (!billing.hasEnoughTokens(tokens)) {
            log.warn("토큰 부족: memberId={}, required={}, current={}",
                    memberId, tokens, billing.getCurrentTokens());
            throw new IllegalStateException("토큰이 부족합니다. 현재: " + billing.getCurrentTokens() + ", 필요: " + tokens);
        }

        billing.consumeTokens(tokens);
        billingRepository.save(billing);

        log.info("토큰 차감: memberId={}, consumed={}, remaining={}",
                memberId, tokens, billing.getCurrentTokens());

        return billing.getCurrentTokens();
    }

    /**
     * 토큰 보유 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean hasEnoughTokens(Long memberId, int required) {
        return billingRepository.findByMemberId(memberId)
                .map(billing -> billing.hasEnoughTokens(required))
                .orElse(false);
    }

    /**
     * 현재 토큰 잔액 조회
     */
    @Transactional(readOnly = true)
    public int getCurrentTokens(Long memberId) {
        return billingRepository.findByMemberId(memberId)
                .map(Billing::getCurrentTokens)
                .orElse(0);
    }
}
