package com.budgetops.backend.billing.service;

import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.enums.PaymentStatus;
import com.budgetops.backend.billing.repository.PaymentRepository;
import com.budgetops.backend.domain.user.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Service 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Member testMember;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");

        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setMember(testMember);
        // 결제 완료 상태 + impUid 설정으로 등록된 결제 수단을 표현
        testPayment.setStatus(PaymentStatus.PAID);
        testPayment.setImpUid("imp_1234567890");
        testPayment.setCustomerUid("customer_123");
    }

    @Test
    @DisplayName("isPaymentRegistered - 결제 수단 등록됨")
    void isPaymentRegistered_True() {
        // given
        given(paymentRepository.findByMember(testMember)).willReturn(Optional.of(testPayment));

        // when
        boolean result = paymentService.isPaymentRegistered(testMember);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isPaymentRegistered - 결제 수단 미등록")
    void isPaymentRegistered_False() {
        // given
        given(paymentRepository.findByMember(testMember)).willReturn(Optional.empty());

        // when
        boolean result = paymentService.isPaymentRegistered(testMember);

        // then
        assertThat(result).isFalse();
    }
}

