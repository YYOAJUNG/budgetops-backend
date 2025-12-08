package com.budgetops.backend.ai.service;

import com.budgetops.backend.ai.config.GeminiConfig;
import com.budgetops.backend.ai.dto.ChatRequest;
import com.budgetops.backend.ai.dto.ChatResponse;
import com.budgetops.backend.costs.CostOptimizationRuleLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AIChatService {
    
    private final GeminiConfig geminiConfig;
    private final CostOptimizationRuleLoader ruleLoader;
    private final MCPContextBuilder mcpContextBuilder;
    private final com.budgetops.backend.billing.service.BillingService billingService;
    private final Map<String, List<Map<String, String>>> chatSessions = new HashMap<>();
    private final WebClient webClient;

    public AIChatService(GeminiConfig geminiConfig,
                         CostOptimizationRuleLoader ruleLoader,
                         MCPContextBuilder mcpContextBuilder,
                         com.budgetops.backend.billing.service.BillingService billingService) {
        this.geminiConfig = geminiConfig;
        this.ruleLoader = ruleLoader;
        this.mcpContextBuilder = mcpContextBuilder;
        this.billingService = billingService;
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
    }
    
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty() || !chatSessions.containsKey(sessionId)) {
            sessionId = UUID.randomUUID().toString();
            chatSessions.put(sessionId, new ArrayList<>());
            log.info("Created new chat session: {}", sessionId);
        }
        
        List<Map<String, String>> history = chatSessions.get(sessionId);
        
        // 사용자 메시지 추가
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("parts", request.getMessage());
        history.add(userMessage);
        
        // 시스템 프롬프트 생성 (실패해도 기본 프롬프트 사용)
        String systemPrompt;
        try {
            systemPrompt = buildSystemPrompt();
        } catch (Exception e) {
            log.error("Failed to build system prompt, using default", e);
            systemPrompt = "당신은 BudgetOps의 클라우드 비용 최적화 전문 AI 어시스턴트입니다. " +
                    "사용자의 질문에 친절하고 전문적으로 답변하세요. " +
                    "비용 최적화와 관련된 구체적인 조언을 제공하세요. " +
                    "답변은 한국어로 작성하고, 마크다운 문법을 사용하지 마세요.";
        }
        
        try {
            // Gemini API 호출
            GeminiApiResponse geminiResponse = callGeminiAPI(systemPrompt, history);
            String response = geminiResponse.getText();

            // AI 응답 추가
            Map<String, String> aiMessage = new HashMap<>();
            aiMessage.put("role", "model");
            aiMessage.put("parts", response);
            history.add(aiMessage);

            // 히스토리 크기 제한 (최근 20개 메시지만 유지)
            if (history.size() > 20) {
                history.subList(0, history.size() - 20).clear();
            }

            // 토큰 차감 전에 먼저 확인
            Long memberId = getCurrentMemberId();
            Integer remainingTokens = null;

            if (memberId != null && geminiResponse.getTotalTokens() != null) {
                int actualTokenUsed = geminiResponse.getTotalTokens();
                int tokensToDeduct = (int) Math.ceil(actualTokenUsed / 2.0);  // 반만 차감 (올림)

                // 토큰 부족 여부 미리 확인
                if (!billingService.hasEnoughTokens(memberId, tokensToDeduct)) {
                    int currentTokens = billingService.getCurrentTokens(memberId);
                    log.warn("토큰 부족으로 AI 응답 차단: memberId={}, required={}, current={}",
                            memberId, tokensToDeduct, currentTokens);

                    // 토큰 부족 안내 메시지 반환 (에러가 아닌 정상 응답으로)
                    String insufficientTokenMessage = String.format(
                            "토큰이 부족하여 AI 응답을 생성할 수 없습니다.\n\n" +
                            "현재 보유 토큰: %d개\n" +
                            "필요한 토큰: %d개\n\n" +
                            "토큰을 충전하시거나 PRO 플랜으로 업그레이드해주세요.",
                            currentTokens, tokensToDeduct
                    );

                    return ChatResponse.builder()
                            .response(insufficientTokenMessage)
                            .sessionId(sessionId)
                            .tokenUsage(null)
                            .remainingTokens(currentTokens)
                            .build();
                }

                // 토큰 차감 (실제 사용량의 50%만 차감)
                try {
                    remainingTokens = billingService.consumeTokens(memberId, tokensToDeduct);
                    log.info("토큰 차감 완료: memberId={}, actualUsed={}, deducted={}, remaining={}",
                            memberId, actualTokenUsed, tokensToDeduct, remainingTokens);
                } catch (IllegalStateException e) {
                    // 이중 확인에도 실패한 경우 (동시성 문제 등)
                    int currentTokens = billingService.getCurrentTokens(memberId);
                    log.error("토큰 차감 실패 (동시성 이슈 가능): {}", e.getMessage());

                    String insufficientTokenMessage = String.format(
                            "토큰이 부족하여 AI 응답을 생성할 수 없습니다.\n\n" +
                            "현재 보유 토큰: %d개\n\n" +
                            "토큰을 충전하시거나 PRO 플랜으로 업그레이드해주세요.",
                            currentTokens
                    );

                    return ChatResponse.builder()
                            .response(insufficientTokenMessage)
                            .sessionId(sessionId)
                            .tokenUsage(null)
                            .remainingTokens(currentTokens)
                            .build();
                }
            }

            return ChatResponse.builder()
                    .response(response)
                    .sessionId(sessionId)
                    .tokenUsage(ChatResponse.TokenUsage.builder()
                            .promptTokens(geminiResponse.getPromptTokens())
                            .completionTokens(geminiResponse.getCompletionTokens())
                            .totalTokens(geminiResponse.getTotalTokens())
                            .build())
                    .remainingTokens(remainingTokens)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get response from Gemini API", e);
            throw new RuntimeException("AI 응답 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 BudgetOps의 클라우드 비용 최적화 전문 AI 어시스턴트입니다.\n\n");
        
        // MCP 컨텍스트 추가 (예외 발생 시에도 계속 진행)
        try {
            Long memberId = getCurrentMemberId();
            if (memberId != null) {
                try {
                    MCPContextBuilder.MCPContext mcpContext = mcpContextBuilder.buildContext(memberId);
                    String contextText = mcpContextBuilder.formatContextForPrompt(mcpContext);
                    // 프롬프트가 너무 길어지지 않도록 제한 (약 8000자)
                    if (contextText.length() > 8000) {
                        log.warn("MCP context too long ({} chars), truncating", contextText.length());
                        contextText = contextText.substring(0, 8000) + "\n\n(일부 내용이 생략되었습니다.)\n";
                    }
                    prompt.append(contextText);
                    prompt.append("\n");
                } catch (Exception e) {
                    log.error("Failed to build MCP context for member {}: {}", memberId, e.getMessage(), e);
                    prompt.append("리소스 정보를 불러오지 못했습니다. 규칙 기반 답변을 제공합니다.\n\n");
                }
            }
        } catch (Exception e) {
            log.error("Failed to get member ID for MCP context: {}", e.getMessage(), e);
            prompt.append("리소스 정보를 불러오지 못했습니다. 규칙 기반 답변을 제공합니다.\n\n");
        }
        
        // 최적화 규칙 추가
        prompt.append("=== 클라우드 비용 최적화 규칙 ===\n\n");
        prompt.append(ruleLoader.formatRulesForPrompt());
        prompt.append("\n");
        
        // 답변 가이드라인
        prompt.append("=== 답변 작성 가이드라인 ===\n\n");
        prompt.append("1. 답변 스타일:\n");
        prompt.append("   - '~한다면 ~하세요' 형식이 아닌 '~하기 때문에 ~하세요' 형식으로 답변하세요.\n");
        prompt.append("   - 실제 리소스 데이터를 분석한 결과를 바탕으로 구체적인 권고를 제시하세요.\n");
        prompt.append("   - 예: '현재 CPU 사용률이 7일간 평균 15%이기 때문에, 더 작은 인스턴스 타입으로 변경하여 비용을 절감하세요.'\n\n");
        prompt.append("2. 리소스 기반 분석:\n");
        prompt.append("   - 위에 제공된 실제 리소스 현황을 기반으로 분석하세요.\n");
        prompt.append("   - AWS, Azure, GCP, NCP 등 모든 CSP의 리소스와 비용 정보를 고려하여 답변하세요.\n");
        prompt.append("   - 특정 리소스나 계정에 대해 질문받으면, 해당 리소스의 실제 데이터를 참고하여 답변하세요.\n");
        prompt.append("   - 리소스 이름, 타입, 상태 등 구체적인 정보를 활용하여 답변하세요.\n");
        prompt.append("   - 중요: 특정 CSP의 비용 데이터가 없다고 해서 '비용 데이터가 없습니다'라고만 답변하지 말고, ");
        prompt.append("해당 CSP의 리소스 현황을 기반으로 최적화 권고를 제시하세요.\n\n");
        prompt.append("3. 최적화 권고:\n");
        prompt.append("   - 규칙과 실제 리소스 데이터를 매칭하여 최적화 기회를 식별하세요.\n");
        prompt.append("   - 각 권고에는 구체적인 이유(리소스 상태, 메트릭 값 등)를 포함하세요.\n");
        prompt.append("   - 예상 절감액이나 비용 절감 효과를 구체적으로 제시하세요.\n\n");
        prompt.append("4. 답변 형식:\n");
        prompt.append("   - 답변은 한국어로 작성하세요.\n");
        prompt.append("   - 마크다운 문법을 사용하지 마세요 (---, ###, **, # 등 사용 금지).\n");
        prompt.append("   - 일반 텍스트로만 작성하고, 줄바꿈으로 구조를 표현하세요.\n");
        prompt.append("   - 친절하고 전문적인 톤을 유지하세요.");
        
        return prompt.toString();
    }
    
    private Long getCurrentMemberId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long) {
                return (Long) principal;
            }
        } catch (Exception e) {
            log.debug("Failed to get current member ID: {}", e.getMessage());
        }
        return null;
    }
    
    private GeminiApiResponse callGeminiAPI(String systemPrompt, List<Map<String, String>> history) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            
            // System Instruction 설정 (Gemini 2.5 Flash 지원)
            Map<String, Object> systemInstruction = new HashMap<>();
            List<Map<String, String>> systemParts = new ArrayList<>();
            Map<String, String> systemPart = new HashMap<>();
            systemPart.put("text", systemPrompt);
            systemParts.add(systemPart);
            systemInstruction.put("parts", systemParts);
            requestBody.put("systemInstruction", systemInstruction);
            
            // Contents 구조 생성 (히스토리)
            List<Map<String, Object>> contents = new ArrayList<>();
            
            // 히스토리 추가
            for (Map<String, String> msg : history) {
                Map<String, Object> content = new HashMap<>();
                content.put("role", msg.get("role"));
                List<Map<String, String>> parts = new ArrayList<>();
                Map<String, String> part = new HashMap<>();
                part.put("text", msg.get("parts"));
                parts.add(part);
                content.put("parts", parts);
                contents.add(content);
            }
            
            requestBody.put("contents", contents);
            
            // GenerationConfig 설정
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 8192); // 답변이 끊기는 문제 해결을 위해 증가
            requestBody.put("generationConfig", generationConfig);
            
            String url = String.format("/models/%s:generateContent?key=%s", 
                    geminiConfig.getModelName(), geminiConfig.getApiKey());
            
            log.debug("Calling Gemini API: {}", url);
            log.debug("System prompt length: {} characters", systemPrompt.length());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response;
            try {
                response = webClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(50)) // 타임아웃 증가 (30초 -> 50초)
                        .block();
            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                log.error("Gemini API HTTP 오류: {} - {}", e.getStatusCode(), e.getMessage());
                if (e.getStatusCode().value() == 503) {
                    throw new RuntimeException("Gemini API가 일시적으로 과부하 상태입니다. 잠시 후 다시 시도해주세요.", e);
                }
                throw new RuntimeException("Gemini API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("Gemini API 호출 중 예외 발생", e);
                throw new RuntimeException("Gemini API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
            }
            
            if (response == null) {
                throw new RuntimeException("Gemini API 응답이 null입니다.");
            }
            
            // 에러 체크
            if (response.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                String errorMessage = (String) error.get("message");
                String errorCode = error.get("code") != null ? error.get("code").toString() : "UNKNOWN";
                
                // 503 오류인 경우 사용자 친화적인 메시지 제공
                if ("503".equals(errorCode) || errorMessage != null && errorMessage.contains("overloaded")) {
                    log.warn("Gemini API 과부하: {}", errorMessage);
                    throw new RuntimeException("Gemini API가 일시적으로 과부하 상태입니다. 잠시 후 다시 시도해주세요.");
                }
                
                log.error("Gemini API 오류 [{}]: {}", errorCode, errorMessage);
                throw new RuntimeException("Gemini API 오류: " + errorMessage);
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            
            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("Gemini API 응답에 candidates가 없습니다.");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            
            if (content == null) {
                throw new RuntimeException("Gemini API 응답에 content가 없습니다.");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");

            if (parts == null || parts.isEmpty()) {
                throw new RuntimeException("Gemini API 응답에 parts가 없습니다.");
            }

            String text = parts.get(0).get("text");

            // usageMetadata 파싱
            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer totalTokens = null;

            if (response.containsKey("usageMetadata")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> usageMetadata = (Map<String, Object>) response.get("usageMetadata");

                promptTokens = usageMetadata.get("promptTokenCount") != null
                        ? ((Number) usageMetadata.get("promptTokenCount")).intValue() : null;
                completionTokens = usageMetadata.get("candidatesTokenCount") != null
                        ? ((Number) usageMetadata.get("candidatesTokenCount")).intValue() : null;
                totalTokens = usageMetadata.get("totalTokenCount") != null
                        ? ((Number) usageMetadata.get("totalTokenCount")).intValue() : null;

                log.debug("Token usage - prompt: {}, completion: {}, total: {}",
                        promptTokens, completionTokens, totalTokens);
            }

            return new GeminiApiResponse(text, promptTokens, completionTokens, totalTokens);

        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            throw new RuntimeException("Gemini API 호출 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * Gemini API 응답 래퍼 클래스
     */
    private static class GeminiApiResponse {
        private final String text;
        private final Integer promptTokens;
        private final Integer completionTokens;
        private final Integer totalTokens;

        public GeminiApiResponse(String text, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
            this.text = text;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public String getText() {
            return text;
        }

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }
    }
}

