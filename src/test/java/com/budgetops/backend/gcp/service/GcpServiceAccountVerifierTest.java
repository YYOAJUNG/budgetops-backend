package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.ServiceAccountTestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GcpServiceAccountVerifier 테스트")
class GcpServiceAccountVerifierTest {

    private final GcpServiceAccountVerifier verifier = new GcpServiceAccountVerifier();

    @Test
    @DisplayName("서비스 계정 검증 - 빈 파라미터")
    void verifyServiceAccount_EmptyParameters() {
        // when
        ServiceAccountTestResponse response = verifier.verifyServiceAccount("", "");

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("비어 있습니다");
        assertThat(response.getMissingRoles()).isNotEmpty();
    }

    @Test
    @DisplayName("서비스 계정 검증 - null 파라미터")
    void verifyServiceAccount_NullParameters() {
        // when
        ServiceAccountTestResponse response = verifier.verifyServiceAccount(null, null);

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("비어 있습니다");
    }

    @Test
    @DisplayName("서비스 계정 검증 - 잘못된 JSON")
    void verifyServiceAccount_InvalidJson() {
        // when
        ServiceAccountTestResponse response = verifier.verifyServiceAccount(
                "test@project.iam.gserviceaccount.com",
                "invalid json"
        );

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("실패");
    }
}

