package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.BillingTestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GcpBillingAccountVerifier 테스트")
class GcpBillingAccountVerifierTest {

    private final GcpBillingAccountVerifier verifier = new GcpBillingAccountVerifier();

    @Test
    @DisplayName("빌링 계정 검증 - 빈 파라미터")
    void verifyBilling_EmptyParameters() {
        // when
        BillingTestResponse response = verifier.verifyBilling("", "");

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("비어 있습니다");
        assertThat(response.isDatasetExists()).isFalse();
    }

    @Test
    @DisplayName("빌링 계정 검증 - null 파라미터")
    void verifyBilling_NullParameters() {
        // when
        BillingTestResponse response = verifier.verifyBilling(null, null);

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("비어 있습니다");
    }

    @Test
    @DisplayName("빌링 계정 검증 - 잘못된 JSON")
    void verifyBilling_InvalidJson() {
        // when
        BillingTestResponse response = verifier.verifyBilling(
                "012345-678901-234567",
                "invalid json"
        );

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("실패");
    }
}

