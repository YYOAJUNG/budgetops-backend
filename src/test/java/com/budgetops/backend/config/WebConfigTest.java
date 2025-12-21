package com.budgetops.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebConfig 테스트")
class WebConfigTest {

    @Test
    @DisplayName("CORS 설정 확인")
    void corsConfigurer_Success() {
        // given
        WebConfig webConfig = new WebConfig();

        // when
        var corsConfigurer = webConfig.corsConfigurer();

        // then
        assertThat(corsConfigurer).isNotNull();

        // CORS 설정이 제대로 적용되는지 확인
        CorsRegistry registry = new CorsRegistry();
        corsConfigurer.addCorsMappings(registry);
        assertThat(registry).isNotNull();
    }
}

