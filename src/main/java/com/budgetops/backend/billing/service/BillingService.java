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
}
