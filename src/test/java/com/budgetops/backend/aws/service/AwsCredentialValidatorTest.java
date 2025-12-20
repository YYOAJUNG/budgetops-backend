package com.budgetops.backend.aws.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AwsCredentialValidator 테스트")
class AwsCredentialValidatorTest {

    private final AwsCredentialValidator validator = new AwsCredentialValidator();

    @Test
    @DisplayName("유효하지 않은 자격 증명 검증")
    void isValid_InvalidCredentials() {
        // when
        boolean result = validator.isValid("invalid-key", "invalid-secret", "us-east-1");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("null 자격 증명 검증")
    void isValid_NullCredentials() {
        // when
        boolean result = validator.isValid(null, null, "us-east-1");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("빈 자격 증명 검증")
    void isValid_EmptyCredentials() {
        // when
        boolean result = validator.isValid("", "", "us-east-1");

        // then
        assertThat(result).isFalse();
    }
}

