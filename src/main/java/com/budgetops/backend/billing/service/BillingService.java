package com.budgetops.backend.billing.service;

import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.entity.Member;
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
                .build();

        billing.setNextBillingDateFromNow();

        log.info("빌링 초기화: memberId={}, plan=FREE", member.getId());
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

        // PRO 요금제로 변경 시 결제 정보 확인
        if (!newPlan.isFree()) {
            boolean isPaymentRegistered = paymentService.isPaymentRegistered(member);
            if (!isPaymentRegistered) {
                throw new PaymentRequiredException();
            }
        }

        // 요금제 변경
        billing.changePlan(newPlan);

        // 유료 플랜으로 변경 시 다음 청구일 설정 (현재 시간 + 1개월)
        if (!newPlan.isFree()) {
            billing.setNextBillingDateFromNow();
        }

        log.info("요금제 변경: memberId={}, newPlan={}, price={}",
                member.getId(), newPlan, billing.getCurrentPrice());

        return billingRepository.save(billing);
    }

    /**
     * 구독 취소
     * - 즉시 다운그레이드하지 않고 상태만 CANCELED로 변경
     * - nextBillingDate까지는 현재 플랜 유지
     */
    public Billing cancelSubscription(Member member) {
        Billing billing = billingRepository.findByMember(member)
                .orElseThrow(() -> new BillingNotFoundException(member.getId()));

        if (!billing.isActive()) {
            throw new IllegalStateException("이미 취소된 구독입니다.");
        }

        if (billing.isFreePlan()) {
            throw new IllegalStateException("무료 플랜은 취소할 수 없습니다.");
        }

        billing.cancelSubscription();

        log.info("구독 취소: memberId={}, canceledAt={}, nextBillingDate={}",
                member.getId(), billing.getCanceledAt(), billing.getNextBillingDate());

        return billingRepository.save(billing);
    }

    /**
     * 만료된 구독 확인 및 처리
     * - CANCELED 상태이고 nextBillingDate가 지났다면 FREE 플랜으로 다운그레이드
     */
    public void checkAndHandleExpiredSubscription(Member member) {
        billingRepository.findByMember(member).ifPresent(billing -> {
            if (billing.isExpired()) {
                log.info("만료된 구독 처리 시작: memberId={}, currentPlan={}, nextBillingDate={}",
                        member.getId(), billing.getCurrentPlan(), billing.getNextBillingDate());

                billing.downgradeToFree();
                billingRepository.save(billing);

                log.info("만료된 구독 처리 완료: memberId={}, newPlan=FREE", member.getId());
            }
        });
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
        billingRepository.findByMember(member).ifPresent(billing -> {
            billingRepository.delete(billing);
            log.info("빌링 정보 삭제: memberId={}", member.getId());
        });
    }
}
