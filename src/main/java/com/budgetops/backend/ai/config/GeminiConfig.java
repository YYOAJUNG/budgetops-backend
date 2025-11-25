package com.budgetops.backend.ai.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class GeminiConfig {
    
    @Value("${gemini.api.key:}")
    private String apiKey;
    
    @Value("${gemini.model.name:gemini-2.5-flash}")
    private String modelName;
    
    public String getApiKey() {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY 환경 변수가 설정되지 않았습니다.");
        }
        return apiKey;
    }
}

