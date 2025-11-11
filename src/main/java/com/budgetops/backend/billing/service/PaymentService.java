package com.budgetops.backend.billing.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.enums.PaymentStatus;
import com.budgetops.backend.billing.exception.PaymentVerificationException;
import com.budgetops.backend.billing.repository.PaymentRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.response.IamportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
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
     * 결제 정보 저장/업데이트
     */
    public Payment savePayment(String impUid, Member member) {
        Optional<Payment> existingPayment = paymentRepository.findByMember(member);

        if (existingPayment.isPresent()) {
            // 기존 결제 정보 업데이트
            Payment payment = existingPayment.get();
            payment.setImpUid(impUid);
            payment.setStatus(PaymentStatus.PAID);
            payment.setLastVerifiedAt(LocalDateTime.now());

            log.info("결제 정보 업데이트: memberId={}, impUid={}", member.getId(), impUid);
            return paymentRepository.save(payment);
        } else {
            // 새로운 결제 정보 생성
            Payment newPayment = Payment.builder()
                    .member(member)
                    .impUid(impUid)
                    .status(PaymentStatus.PAID)
                    .lastVerifiedAt(LocalDateTime.now())
                    .build();

            log.info("결제 정보 생성: memberId={}, impUid={}", member.getId(), impUid);
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
}
