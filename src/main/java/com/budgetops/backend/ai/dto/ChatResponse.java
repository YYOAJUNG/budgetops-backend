package com.budgetops.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private String sessionId;
    private TokenUsage tokenUsage;
    private Integer remainingTokens;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private Integer promptTokens;      // 입력 토큰
        private Integer completionTokens;  // 출력 토큰
        private Integer totalTokens;       // 총 토큰
    }
}

