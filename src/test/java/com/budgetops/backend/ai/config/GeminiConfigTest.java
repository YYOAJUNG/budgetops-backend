package com.budgetops.backend.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GeminiConfig 테스트")
class GeminiConfigTest {

    @Test
    @DisplayName("API 키가 설정된 경우 정상 동작")
    void getApiKey_WithValidKey_Success() {
        // given
        GeminiConfig config = new GeminiConfig();
        ReflectionTestUtils.setField(config, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(config, "modelName", "gemini-2.5-flash");

        // when
        String apiKey = config.getApiKey();

        // then
        assertThat(apiKey).isEqualTo("test-api-key");
    }

    @Test
    @DisplayName("API 키가 비어있는 경우 예외 발생")
    void getApiKey_WithEmptyKey_ThrowsException() {
        // given
        GeminiConfig config = new GeminiConfig();
        ReflectionTestUtils.setField(config, "apiKey", "");
        ReflectionTestUtils.setField(config, "modelName", "gemini-2.5-flash");

        // when & then
        assertThatThrownBy(() -> config.getApiKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY 환경 변수가 설정되지 않았습니다");
    }

    @Test
    @DisplayName("API 키가 null인 경우 예외 발생")
    void getApiKey_WithNullKey_ThrowsException() {
        // given
        GeminiConfig config = new GeminiConfig();
        ReflectionTestUtils.setField(config, "apiKey", null);
        ReflectionTestUtils.setField(config, "modelName", "gemini-2.5-flash");

        // when & then
        assertThatThrownBy(() -> config.getApiKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY 환경 변수가 설정되지 않았습니다");
    }
}

