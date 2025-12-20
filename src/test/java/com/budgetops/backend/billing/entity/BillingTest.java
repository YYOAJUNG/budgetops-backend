package com.budgetops.backend.billing.entity;

import com.budgetops.backend.billing.enums.BillingPlan;
import com.budgetops.backend.domain.user.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Billing 엔티티 테스트")
class BillingTest {

    private Billing billing;
    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .lastLoginAt(LocalDateTime.now())
                .build();

        billing = Billing.builder()
                .member(member)
                .currentPlan(BillingPlan.FREE)
                .currentPrice(0)
                .currentTokens(10000)
                .build();
    }

    @Test
    @DisplayName("요금제 변경")
    void changePlan_Success() {
        // when
        billing.changePlan(BillingPlan.PRO);

        // then
        assertThat(billing.getCurrentPlan()).isEqualTo(BillingPlan.PRO);
        assertThat(billing.getCurrentPrice()).isEqualTo(BillingPlan.PRO.getTotalPrice());
    }

    @Test
    @DisplayName("토큰 추가")
    void addTokens_Success() {
        // given
        int initialTokens = billing.getCurrentTokens();

        // when
        billing.addTokens(5000);

        // then
        assertThat(billing.getCurrentTokens()).isEqualTo(initialTokens + 5000);
    }

    @Test
    @DisplayName("토큰 차감 성공")
    void consumeTokens_Success() {
        // given
        billing.setCurrentTokens(10000);

        // when
        billing.consumeTokens(3000);

        // then
        assertThat(billing.getCurrentTokens()).isEqualTo(7000);
    }

    @Test
    @DisplayName("토큰 차감 실패 - 토큰 부족")
    void consumeTokens_InsufficientTokens() {
        // given
        billing.setCurrentTokens(1000);

        // when & then
        assertThatThrownBy(() -> billing.consumeTokens(2000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("토큰이 부족합니다");
    }

    @Test
    @DisplayName("토큰 보유 여부 확인 - 충분한 토큰")
    void hasEnoughTokens_Sufficient() {
        // given
        billing.setCurrentTokens(10000);

        // when & then
        assertThat(billing.hasEnoughTokens(5000)).isTrue();
    }

    @Test
    @DisplayName("토큰 보유 여부 확인 - 부족한 토큰")
    void hasEnoughTokens_Insufficient() {
        // given
        billing.setCurrentTokens(1000);

        // when & then
        assertThat(billing.hasEnoughTokens(5000)).isFalse();
    }

    @Test
    @DisplayName("무료 요금제 확인")
    void isFreePlan_True() {
        // given
        billing.setCurrentPlan(BillingPlan.FREE);

        // when & then
        assertThat(billing.isFreePlan()).isTrue();
    }

    @Test
    @DisplayName("무료 요금제 확인 - PRO 플랜")
    void isFreePlan_False() {
        // given
        billing.setCurrentPlan(BillingPlan.PRO);

        // when & then
        assertThat(billing.isFreePlan()).isFalse();
    }

    @Test
    @DisplayName("다음 청구일 설정")
    void setNextBillingDateFromNow_Success() {
        // given
        LocalDateTime before = LocalDateTime.now();
        billing.setNextBillingDate(null);

        // when
        billing.setNextBillingDateFromNow();

        // then
        assertThat(billing.getNextBillingDate()).isNotNull();
        assertThat(billing.getNextBillingDate()).isAfter(before);
    }
}

