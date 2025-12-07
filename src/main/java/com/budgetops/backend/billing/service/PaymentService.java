package com.budgetops.backend.billing.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.entity.PaymentHistory;
import com.budgetops.backend.billing.enums.PaymentStatus;
import com.budgetops.backend.billing.exception.PaymentVerificationException;
import com.budgetops.backend.billing.repository.PaymentRepository;
import com.budgetops.backend.billing.repository.PaymentHistoryRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.AgainPaymentData;
import com.siot.IamportRestClient.response.IamportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final IamportClient iamportClient;

    /**
     * 결제 검증 (Iamport API 호출)
     * 개발 환경에서는 Mock impUid는 검증을 건너뜁니다.
     */
    public void verifyPayment(String impUid) {
        // Mock 결제 처리 (개발용)
        if (impUid != null && impUid.startsWith("billing_mock_")) {
            log.info("Mock 결제 검증 스킵: impUid={}", impUid);
            return;
        }

        try {
            IamportResponse<com.siot.IamportRestClient.response.Payment> iamportResponse =
                iamportClient.paymentByImpUid(impUid);

            if (iamportResponse.getResponse() == null) {
                throw new PaymentVerificationException(impUid);
            }

            String status = iamportResponse.getResponse().getStatus();
            if (!PaymentStatus.PAID.getKey().equalsIgnoreCase(status)) {
                throw new PaymentVerificationException(impUid, status);
            }

            log.info("결제 검증 성공: impUid={}, status={}", impUid, status);

        } catch (IamportResponseException | IOException e) {
            log.error("결제 검증 실패: impUid={}, error={}", impUid, e.getMessage());
            throw new PaymentVerificationException("결제 검증에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 결제 정보 등록 (검증 + 저장)
     */
    public Payment registerPayment(String impUid, String customerUid, Member member) {
        verifyPayment(impUid);
        return savePayment(impUid, customerUid, member);
    }

    /**
     * 결제 정보 저장/업데이트
     */
    public Payment savePayment(String impUid, String customerUid, Member member) {
        Optional<Payment> existingPayment = paymentRepository.findByMember(member);

        if (existingPayment.isPresent()) {
            // 기존 결제 정보 업데이트
            Payment payment = existingPayment.get();
            payment.setImpUid(impUid);
            payment.setCustomerUid(customerUid);
            payment.setStatus(PaymentStatus.PAID);
            payment.setLastVerifiedAt(LocalDateTime.now());

            log.info("결제 정보 업데이트: memberId={}, impUid={}, customerUid={}",
                    member.getId(), impUid, customerUid);
            return paymentRepository.save(payment);
        } else {
            // 새로운 결제 정보 생성
            Payment newPayment = Payment.builder()
                    .member(member)
                    .impUid(impUid)
                    .customerUid(customerUid)
                    .status(PaymentStatus.PAID)
                    .lastVerifiedAt(LocalDateTime.now())
                    .build();

            log.info("결제 정보 생성: memberId={}, impUid={}, customerUid={}",
                    member.getId(), impUid, customerUid);
            return paymentRepository.save(newPayment);
        }
    }

    /**
     * 결제 등록 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isPaymentRegistered(Member member) {
        Optional<Payment> payment = paymentRepository.findByMember(member);
        boolean isRegistered = payment.isPresent() && payment.get().isRegistered();

        log.debug("결제 등록 확인: memberId={}, registered={}", member.getId(), isRegistered);
        return isRegistered;
    }

    /**
     * 사용자의 결제 정보 조회
     */
    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentByMember(Member member) {
        return paymentRepository.findByMember(member);
    }

    /**
     * 결제 정보 삭제
     */
    public void deletePayment(Member member) {
        Optional<Payment> payment = paymentRepository.findByMember(member);
        payment.ifPresent(p -> {
            paymentRepository.delete(p);
            log.info("결제 정보 삭제: memberId={}", member.getId());
        });
    }

    /**
     * 빌링키를 사용한 자동 결제
     */
    public String payWithBillingKey(Member member, String merchantUid, String orderName, int amount) {
        Payment payment = paymentRepository.findByMember(member)
                .orElseThrow(() -> new IllegalStateException("등록된 결제 수단이 없습니다."));

        String customerUid = payment.getCustomerUid();
        if (customerUid == null || customerUid.isEmpty()) {
            throw new IllegalStateException("빌링키가 등록되지 않았습니다.");
        }

        try {
            // PortOne API를 통한 빌링키 결제 (againPayment 메서드 사용)
            AgainPaymentData againData = new AgainPaymentData(
                    customerUid,
                    merchantUid,
                    BigDecimal.valueOf(amount)
            );
            againData.setName(orderName);

            IamportResponse<com.siot.IamportRestClient.response.Payment> response =
                    iamportClient.againPayment(againData);

            if (response.getResponse() == null) {
                throw new PaymentVerificationException("빌링키 결제 실패");
            }

            String impUid = response.getResponse().getImpUid();
            String payMethod = response.getResponse().getPayMethod();

            // 결제 내역 저장
            savePaymentHistory(member, impUid, merchantUid, amount, orderName, payMethod);

            log.info("빌링키 결제 성공: memberId={}, impUid={}, amount={}", member.getId(), impUid, amount);
            return impUid;

        } catch (IamportResponseException | IOException e) {
            log.error("빌링키 결제 실패: memberId={}, error={}", member.getId(), e.getMessage());

            // 실패한 결제 내역도 저장
            saveFailedPaymentHistory(member, merchantUid, amount, orderName, e.getMessage());

            throw new PaymentVerificationException("빌링키 결제에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 결제 내역 저장 (성공)
     */
    public PaymentHistory savePaymentHistory(Member member, String impUid, String merchantUid,
                                             Integer amount, String orderName, String paymentMethod) {
        PaymentHistory history = PaymentHistory.builder()
                .member(member)
                .impUid(impUid)
                .merchantUid(merchantUid)
                .amount(amount)
                .orderName(orderName)
                .paymentMethod(paymentMethod)
                .status(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now())
                .build();

        PaymentHistory saved = paymentHistoryRepository.save(history);
        log.info("결제 내역 저장: memberId={}, impUid={}, amount={}", member.getId(), impUid, amount);
        return saved;
    }

    /**
     * 결제 내역 저장 (실패)
     */
    private PaymentHistory saveFailedPaymentHistory(Member member, String merchantUid,
                                                    Integer amount, String orderName, String failedReason) {
        PaymentHistory history = PaymentHistory.builder()
                .member(member)
                .impUid("FAILED_" + merchantUid)
                .merchantUid(merchantUid)
                .amount(amount)
                .orderName(orderName)
                .status(PaymentStatus.FAILED)
                .failedReason(failedReason)
                .build();

        PaymentHistory saved = paymentHistoryRepository.save(history);
        log.info("실패 결제 내역 저장: memberId={}, merchantUid={}, reason={}",
                member.getId(), merchantUid, failedReason);
        return saved;
    }

    /**
     * 회원의 결제 내역 조회
     */
    @Transactional(readOnly = true)
    public List<PaymentHistory> getPaymentHistory(Member member) {
        return paymentHistoryRepository.findByMemberOrderByCreatedAtDesc(member);
    }

    /**
     * 회원 ID로 결제 내역 조회
     */
    @Transactional(readOnly = true)
    public List<PaymentHistory> getPaymentHistoryByMemberId(Long memberId) {
        return paymentHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }
}
