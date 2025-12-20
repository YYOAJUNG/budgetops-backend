package com.budgetops.backend.aws.constants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FreeTierLimits 테스트")
class FreeTierLimitsTest {

    @Test
    @DisplayName("EC2 프리티어 한도 확인")
    void ec2FreeTierLimit_Check() {
        // given & when & then
        assertThat(FreeTierLimits.EC2_FREE_TIER_HOURS_PER_MONTH).isEqualTo(750.0);
    }

    @Test
    @DisplayName("S3 프리티어 한도 확인")
    void s3FreeTierLimit_Check() {
        // given & when & then
        assertThat(FreeTierLimits.S3_FREE_TIER_STORAGE_GB).isEqualTo(5.0);
        assertThat(FreeTierLimits.S3_FREE_TIER_PUT_REQUESTS).isEqualTo(20000L);
        assertThat(FreeTierLimits.S3_FREE_TIER_GET_REQUESTS).isEqualTo(20000L);
    }

    @Test
    @DisplayName("RDS 프리티어 한도 확인")
    void rdsFreeTierLimit_Check() {
        // given & when & then
        assertThat(FreeTierLimits.RDS_FREE_TIER_HOURS_PER_MONTH).isEqualTo(750.0);
        assertThat(FreeTierLimits.RDS_FREE_TIER_STORAGE_GB).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Lambda 프리티어 한도 확인")
    void lambdaFreeTierLimit_Check() {
        // given & when & then
        assertThat(FreeTierLimits.LAMBDA_FREE_TIER_REQUESTS).isEqualTo(1000000L);
        assertThat(FreeTierLimits.LAMBDA_FREE_TIER_COMPUTE_GB_SECONDS).isEqualTo(400000L);
    }

    @Test
    @DisplayName("EC2 프리티어 대상 인스턴스 타입 확인")
    void isFreeTierInstanceType_EC2_Success() {
        // given & when & then
        assertThat(FreeTierLimits.isFreeTierInstanceType("t2.micro")).isTrue();
        assertThat(FreeTierLimits.isFreeTierInstanceType("t3.micro")).isTrue();
        assertThat(FreeTierLimits.isFreeTierInstanceType("t4g.micro")).isTrue();
        assertThat(FreeTierLimits.isFreeTierInstanceType("t2.small")).isFalse();
        assertThat(FreeTierLimits.isFreeTierInstanceType(null)).isFalse();
    }

    @Test
    @DisplayName("EC2 프리티어 적격 여부 확인")
    void isFreeTierEligible_EC2_Success() {
        // given & when & then
        assertThat(FreeTierLimits.isFreeTierEligible("EC2", "INSTANCE", 750.0)).isTrue();
        assertThat(FreeTierLimits.isFreeTierEligible("EC2", "INSTANCE", 751.0)).isFalse();
    }

    @Test
    @DisplayName("S3 프리티어 적격 여부 확인")
    void isFreeTierEligible_S3_Success() {
        // given & when & then
        assertThat(FreeTierLimits.isFreeTierEligible("S3", "STORAGE", 5.0)).isTrue();
        assertThat(FreeTierLimits.isFreeTierEligible("S3", "STORAGE", 6.0)).isFalse();
    }

    @Test
    @DisplayName("RDS 프리티어 적격 여부 확인")
    void isFreeTierEligible_RDS_Success() {
        // given & when & then
        assertThat(FreeTierLimits.isFreeTierEligible("RDS", "INSTANCE", 750.0)).isTrue();
        assertThat(FreeTierLimits.isFreeTierEligible("RDS", "INSTANCE", 751.0)).isFalse();
    }

    @Test
    @DisplayName("Lambda 프리티어 적격 여부 확인")
    void isFreeTierEligible_Lambda_Success() {
        // given & when & then
        assertThat(FreeTierLimits.isFreeTierEligible("LAMBDA", "REQUESTS", 1000000.0)).isTrue();
        assertThat(FreeTierLimits.isFreeTierEligible("LAMBDA", "REQUESTS", 1000001.0)).isFalse();
        assertThat(FreeTierLimits.isFreeTierEligible("LAMBDA", "COMPUTE", 400000.0)).isTrue();
        assertThat(FreeTierLimits.isFreeTierEligible("LAMBDA", "COMPUTE", 400001.0)).isFalse();
    }

    @Test
    @DisplayName("프리티어 초과 시 예상 비용 계산 - EC2")
    void calculateEstimatedCostIfExceeded_EC2_Success() {
        // given & when & then
        double cost = FreeTierLimits.calculateEstimatedCostIfExceeded("EC2", "INSTANCE", 800.0, "t2.micro");
        assertThat(cost).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("프리티어 초과 시 예상 비용 계산 - 프리티어 내 사용")
    void calculateEstimatedCostIfExceeded_WithinFreeTier_ReturnsZero() {
        // given & when & then
        double cost = FreeTierLimits.calculateEstimatedCostIfExceeded("EC2", "INSTANCE", 500.0, "t2.micro");
        assertThat(cost).isEqualTo(0.0);
    }
}

