package com.budgetops.backend.ai.service;

import com.budgetops.backend.ai.config.GeminiConfig;
import com.budgetops.backend.ai.dto.ChatRequest;
import com.budgetops.backend.ai.dto.ChatResponse;
import com.budgetops.backend.costs.CostOptimizationRuleLoader;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatService {
    
    private final GeminiConfig geminiConfig;
    private final CostOptimizationRuleLoader ruleLoader;
    private final Map<String, List<Map<String, String>>> chatSessions = new HashMap<>();
    private final WebClient webClient;
    private final AwsAccountRepository awsAccountRepository;
    private final AwsEc2Service awsEc2Service;
    
    public AIChatService(GeminiConfig geminiConfig,
                         CostOptimizationRuleLoader ruleLoader,
                         AwsAccountRepository awsAccountRepository,
                         AwsEc2Service awsEc2Service) {
        this.geminiConfig = geminiConfig;
        this.ruleLoader = ruleLoader;
        this.awsAccountRepository = awsAccountRepository;
        this.awsEc2Service = awsEc2Service;
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
        
        // 시스템 프롬프트 생성
        String systemPrompt = buildSystemPrompt();
        
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
        
        // 리소스 스냅샷(간단 요약) 추가 시도
        try {
            List<AwsAccount> activeAccounts = awsAccountRepository.findByActiveTrue();
            if (!activeAccounts.isEmpty()) {
                AwsAccount account = activeAccounts.get(0);
                String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                
                long running = instances.stream().filter(i -> "running".equalsIgnoreCase(i.getState())).count();
                long stopped = instances.stream().filter(i -> "stopped".equalsIgnoreCase(i.getState())).count();
                
                prompt.append("사용자 보유 AWS EC2 리소스 요약 (계정 ")
                        .append(account.getName() != null ? account.getName() : account.getId())
                        .append(", 리전 ").append(region).append("): 총 ")
                        .append(instances.size()).append("대 (실행중: ")
                        .append(running).append("대, 중지: ")
                        .append(stopped).append("대)\n\n");
            } else {
                prompt.append("현재 활성화된 AWS 계정이 없습니다.\n\n");
            }
        } catch (Exception e) {
            // 프롬프트 생성 실패 시 무시하고 기본 규칙만 포함
            prompt.append("리소스 요약을 불러오지 못했습니다. 규칙 기반 답변을 제공합니다.\n\n");
        }
        
        prompt.append("사용자의 질문에 친절하고 전문적으로 답변하세요. ");
        prompt.append("비용 최적화와 관련된 구체적인 조언을 제공하고, 위의 규칙을 참고하여 실용적인 권장사항을 제시하세요. ");
        prompt.append("답변은 한국어로 작성하세요.");
        
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
            
            Map<String, Object> response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (response == null) {
                throw new RuntimeException("Gemini API 응답이 null입니다.");
            }
            
            // 에러 체크
            if (response.containsKey("error")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                String errorMessage = (String) error.get("message");
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

