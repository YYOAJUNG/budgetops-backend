package com.budgetops.backend.billing.service;

import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.enums.BillingPlan;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.exception.InvalidBillingPlanException;
import com.budgetops.backend.billing.exception.PaymentRequiredException;
import com.budgetops.backend.billing.repository.BillingRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Billing Service 테스트")
class BillingServiceTest {

    @Mock
    private BillingRepository billingRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private BillingService billingService;

    private Member testMember;
    private Billing testBilling;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");

        testBilling = Billing.builder()
                .id(1L)
                .member(testMember)
                .currentPlan(BillingPlan.FREE)
                .currentPrice(0)
                .currentTokens(10000)
                .build();
    }

    @Test
    @DisplayName("initializeBilling - 빌링 초기화 성공")
    void initializeBilling_Success() {
        // given
        given(billingRepository.save(any(Billing.class))).willAnswer(invocation -> {
            Billing billing = invocation.getArgument(0);
            billing.setId(1L);
            return billing;
        });

        // when
        Billing result = billingService.initializeBilling(testMember);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getCurrentPlan()).isEqualTo(BillingPlan.FREE);
        assertThat(result.getCurrentPrice()).isEqualTo(0);
        assertThat(result.getCurrentTokens()).isEqualTo(10000);
        verify(billingRepository).save(any(Billing.class));
    }

    @Test
    @DisplayName("getBillingByMember - 빌링 정보 조회")
    void getBillingByMember_Success() {
        // given
        given(billingRepository.findByMember(testMember)).willReturn(Optional.of(testBilling));

        // when
        Optional<Billing> result = billingService.getBillingByMember(testMember);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getCurrentPlan()).isEqualTo(BillingPlan.FREE);
    }

    @Test
    @DisplayName("changePlan - 잘못된 플랜 이름")
    void changePlan_InvalidPlanName() {
        // when & then
        assertThatThrownBy(() -> billingService.changePlan(testMember, "INVALID_PLAN"))
                .isInstanceOf(InvalidBillingPlanException.class);
    }

    @Test
    @DisplayName("changePlan - 빌링 정보가 없는 경우")
    void changePlan_BillingNotFound() {
        // given
        given(billingRepository.findByMember(testMember)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> billingService.changePlan(testMember, BillingPlan.PRO))
                .isInstanceOf(BillingNotFoundException.class);
    }

    @Test
    @DisplayName("changePlan - PRO로 변경 시 결제 수단 미등록")
    void changePlan_ProWithoutPayment() {
        // given
        given(billingRepository.findByMember(testMember)).willReturn(Optional.of(testBilling));
        given(paymentService.isPaymentRegistered(testMember)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> billingService.changePlan(testMember, BillingPlan.PRO))
                .isInstanceOf(PaymentRequiredException.class);
    }
}

