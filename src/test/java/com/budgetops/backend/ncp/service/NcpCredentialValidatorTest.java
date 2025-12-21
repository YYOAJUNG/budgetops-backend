package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.client.NcpApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("NcpCredentialValidator 테스트")
class NcpCredentialValidatorTest {

    @Mock
    private NcpApiClient apiClient;

    @InjectMocks
    private NcpCredentialValidator validator;

    @Test
    @DisplayName("유효한 자격 증명 검증")
    void isValid_ValidCredentials() {
        // given
        given(apiClient.callServerApi(anyString(), any(), anyString(), anyString()))
                .willReturn(null);

        // when
        boolean result = validator.isValid("test-access-key", "test-secret-key", "KR");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("유효하지 않은 자격 증명 검증")
    void isValid_InvalidCredentials() {
        // given
        doThrow(new RuntimeException("Invalid credentials"))
                .when(apiClient).callServerApi(anyString(), any(), anyString(), anyString());

        // when
        boolean result = validator.isValid("invalid-key", "invalid-secret", "KR");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("null regionCode로 검증")
    void isValid_NullRegionCode() {
        // given
        given(apiClient.callServerApi(anyString(), any(), anyString(), anyString()))
                .willReturn(null);

        // when
        boolean result = validator.isValid("test-access-key", "test-secret-key", null);

        // then
        assertThat(result).isTrue();
    }
}

