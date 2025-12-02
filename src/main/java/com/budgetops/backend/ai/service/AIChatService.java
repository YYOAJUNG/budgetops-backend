package com.budgetops.backend.ai.service;

import com.budgetops.backend.ai.config.GeminiConfig;
import com.budgetops.backend.ai.dto.ChatRequest;
import com.budgetops.backend.ai.dto.ChatResponse;
import com.budgetops.backend.costs.CostOptimizationRuleLoader;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsCostService;
import com.budgetops.backend.ai.service.ResourceAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AIChatService {
    
    private final GeminiConfig geminiConfig;
    private final CostOptimizationRuleLoader ruleLoader;
    private final Map<String, List<Map<String, String>>> chatSessions = new HashMap<>();
    private final WebClient webClient;
    private final AwsAccountRepository awsAccountRepository;
    private final AwsCostService awsCostService;
    private final ResourceAnalysisService resourceAnalysisService;
    
    public AIChatService(GeminiConfig geminiConfig,
                         CostOptimizationRuleLoader ruleLoader,
                         AwsAccountRepository awsAccountRepository,
                         AwsCostService awsCostService,
                         ResourceAnalysisService resourceAnalysisService) {
        this.geminiConfig = geminiConfig;
        this.ruleLoader = ruleLoader;
        this.awsAccountRepository = awsAccountRepository;
        this.awsCostService = awsCostService;
        this.resourceAnalysisService = resourceAnalysisService;
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
            String response = callGeminiAPI(systemPrompt, history);
            
            // AI 응답 추가
            Map<String, String> aiMessage = new HashMap<>();
            aiMessage.put("role", "model");
            aiMessage.put("parts", response);
            history.add(aiMessage);
            
            // 히스토리 크기 제한 (최근 20개 메시지만 유지)
            if (history.size() > 20) {
                history.subList(0, history.size() - 20).clear();
            }
            
            return ChatResponse.builder()
                    .response(response)
                    .sessionId(sessionId)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to get response from Gemini API", e);
            throw new RuntimeException("AI 응답 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 BudgetOps의 클라우드 비용 최적화 전문 AI 어시스턴트입니다.\n\n");
        prompt.append("다음은 클라우드 비용 최적화를 위한 규칙입니다:\n\n");
        prompt.append(ruleLoader.formatRulesForPrompt());
        prompt.append("\n\n");
        
        // 현재 사용자 ID 가져오기
        Long currentMemberId = null;
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long) {
                currentMemberId = (Long) principal;
            }
        } catch (Exception e) {
            log.debug("Failed to get current member ID: {}", e.getMessage());
        }
        
        // 리소스 기반 분석 수행
        try {
            ResourceAnalysisService.ResourceAnalysisResult resourceAnalysis = 
                    resourceAnalysisService.analyzeAllResources(currentMemberId);
            String resourceAnalysisText = resourceAnalysisService.formatResourceAnalysisForPrompt(resourceAnalysis);
            prompt.append(resourceAnalysisText);
        } catch (Exception e) {
            log.warn("Failed to perform resource analysis: {}", e.getMessage());
        }
        
        // 사용자 리소스 및 비용 정보 추가 (기존 AWS 비용 정보)
        try {
            List<AwsAccount> activeAccounts = awsAccountRepository.findByActiveTrue();
            if (!activeAccounts.isEmpty()) {
                prompt.append("=== 최근 비용 정보 ===\n\n");
                
                // 비용 정보 조회 (최근 30일) - 실패해도 계속 진행
                try {
                    java.time.LocalDate endDate = java.time.LocalDate.now().plusDays(1);
                    java.time.LocalDate startDate = endDate.minusDays(30);
                    String startDateStr = startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                    String endDateStr = endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                    
                    Set<Long> ownerIds = new LinkedHashSet<>();
                    for (AwsAccount account : activeAccounts) {
                        if (account.getOwner() == null) {
                            log.warn("AWS account {} has no owner associated; skipping cost aggregation.", account.getId());
                            continue;
                        }
                        ownerIds.add(account.getOwner().getId());
                    }

                    List<AwsCostService.AccountCost> accountCosts = new ArrayList<>();
                    for (Long ownerId : ownerIds) {
                        try {
                            accountCosts.addAll(awsCostService.getAllAccountsCosts(ownerId, startDateStr, endDateStr));
                        } catch (Exception e) {
                            log.warn("Failed to fetch costs for ownerId {}: {}", ownerId, e.getMessage());
                        }
                    }

                    double totalCost = accountCosts.stream().mapToDouble(AwsCostService.AccountCost::totalCost).sum();
                    
                    prompt.append("📊 최근 30일 비용 요약:\n");
                    prompt.append(String.format("- 전체 AWS 비용: $%.2f USD\n", totalCost));
                    
                    if (!accountCosts.isEmpty()) {
                        prompt.append("- 계정별 비용:\n");
                        for (AwsCostService.AccountCost accountCost : accountCosts) {
                            prompt.append(String.format("  • %s: $%.2f USD\n", 
                                    accountCost.accountName(), accountCost.totalCost()));
                        }
                    } else {
                        prompt.append("- 계정별 비용 데이터를 불러올 수 없습니다 (Cost Explorer 권한 확인 필요)\n");
                    }
                    prompt.append("\n");
                } catch (Exception e) {
                    log.warn("Failed to fetch cost information for prompt: {}", e.getMessage());
                    prompt.append("📊 최근 30일 비용 요약:\n");
                    prompt.append("- 비용 정보를 불러올 수 없습니다 (Cost Explorer 권한 확인 필요)\n\n");
                }
                
                prompt.append("\n");
            }
        } catch (Exception e) {
            log.error("Failed to build resource and cost information", e);
            prompt.append("리소스 및 비용 정보를 불러오지 못했습니다. 규칙 기반 답변을 제공합니다.\n\n");
        }
        
        prompt.append("=== 답변 작성 가이드라인 ===\n\n");
        prompt.append("1. 답변 스타일:\n");
        prompt.append("   - '~한다면 ~하세요' 형식이 아닌 '~하기 때문에 ~하세요' 형식으로 답변하세요.\n");
        prompt.append("   - 실제 리소스 데이터를 분석한 결과를 바탕으로 구체적인 권고를 제시하세요.\n");
        prompt.append("   - 예: '현재 CPU 사용률이 7일간 평균 15%이기 때문에, 더 작은 인스턴스 타입으로 변경하여 비용을 절감하세요.'\n\n");
        prompt.append("2. 리소스 기반 분석:\n");
        prompt.append("   - 위에 제공된 실제 리소스 현황을 기반으로 분석하세요.\n");
        prompt.append("   - 특정 리소스나 계정에 대해 질문받으면, 해당 리소스의 실제 데이터를 참고하여 답변하세요.\n");
        prompt.append("   - 리소스 이름, 타입, 상태 등 구체적인 정보를 활용하여 답변하세요.\n\n");
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
    
    private String callGeminiAPI(String systemPrompt, List<Map<String, String>> history) {
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
            generationConfig.put("maxOutputTokens", 2048);
            requestBody.put("generationConfig", generationConfig);
            
            String url = String.format("/models/%s:generateContent?key=%s", 
                    geminiConfig.getModelName(), geminiConfig.getApiKey());
            
            log.debug("Calling Gemini API: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response;
            try {
                response = webClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(30))
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
            
            return parts.get(0).get("text");
            
        } catch (Exception e) {
            log.error("Gemini API 호출 실패", e);
            throw new RuntimeException("Gemini API 호출 중 오류: " + e.getMessage(), e);
        }
    }
}

