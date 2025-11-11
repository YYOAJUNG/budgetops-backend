package com.budgetops.backend.ai.service;

import com.budgetops.backend.ai.config.GeminiConfig;
import com.budgetops.backend.ai.dto.ChatRequest;
import com.budgetops.backend.ai.dto.ChatResponse;
import com.budgetops.backend.costs.CostOptimizationRuleLoader;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.aws.service.AwsCostService;
import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class AIChatService {
    
    private final GeminiConfig geminiConfig;
    private final CostOptimizationRuleLoader ruleLoader;
    private final Map<String, List<Map<String, String>>> chatSessions = new HashMap<>();
    private final WebClient webClient;
    private final AwsAccountRepository awsAccountRepository;
    private final AwsEc2Service awsEc2Service;
    private final AwsCostService awsCostService;
    
    public AIChatService(GeminiConfig geminiConfig,
                         CostOptimizationRuleLoader ruleLoader,
                         AwsAccountRepository awsAccountRepository,
                         AwsEc2Service awsEc2Service,
                         AwsCostService awsCostService) {
        this.geminiConfig = geminiConfig;
        this.ruleLoader = ruleLoader;
        this.awsAccountRepository = awsAccountRepository;
        this.awsEc2Service = awsEc2Service;
        this.awsCostService = awsCostService;
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
        
        // 사용자 리소스 및 비용 정보 추가
        try {
            List<AwsAccount> activeAccounts = awsAccountRepository.findByActiveTrue();
            if (!activeAccounts.isEmpty()) {
                prompt.append("=== 사용자 클라우드 리소스 및 비용 정보 ===\n\n");
                
                // 비용 정보 조회 (최근 30일)
                java.time.LocalDate endDate = java.time.LocalDate.now().plusDays(1);
                java.time.LocalDate startDate = endDate.minusDays(30);
                String startDateStr = startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                String endDateStr = endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                
                List<AwsCostService.AccountCost> accountCosts = awsCostService.getAllAccountsCosts(startDateStr, endDateStr);
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
                
                // 리소스 정보
                prompt.append("🖥️ AWS EC2 리소스 요약:\n");
                for (AwsAccount account : activeAccounts) {
                    try {
                        String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                        List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                        
                        long running = instances.stream().filter(i -> "running".equalsIgnoreCase(i.getState())).count();
                        long stopped = instances.stream().filter(i -> "stopped".equalsIgnoreCase(i.getState())).count();
                        
                        prompt.append(String.format("- 계정: %s (리전: %s)\n", 
                                account.getName() != null ? account.getName() : "Account " + account.getId(), region));
                        prompt.append(String.format("  총 %d대 (실행중: %d대, 중지: %d대)\n", 
                                instances.size(), running, stopped));
                        
                        // 인스턴스 타입별 요약
                        Map<String, Long> typeCount = new HashMap<>();
                        for (AwsEc2InstanceResponse instance : instances) {
                            String instanceType = instance.getInstanceType() != null ? instance.getInstanceType() : "unknown";
                            typeCount.put(instanceType, typeCount.getOrDefault(instanceType, 0L) + 1);
                        }
                        if (!typeCount.isEmpty()) {
                            prompt.append("  인스턴스 타입별: ");
                            List<String> typeSummary = new ArrayList<>();
                            for (Map.Entry<String, Long> entry : typeCount.entrySet()) {
                                typeSummary.add(entry.getKey() + " x" + entry.getValue());
                            }
                            prompt.append(String.join(", ", typeSummary)).append("\n");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch EC2 instances for account {}: {}", account.getId(), e.getMessage());
                        prompt.append(String.format("- 계정: %s (리소스 조회 실패)\n", 
                                account.getName() != null ? account.getName() : "Account " + account.getId()));
                    }
                }
                prompt.append("\n");
                
                prompt.append("💡 사용 가능한 분석 옵션:\n");
                prompt.append("1. 전체 비용 분석: 모든 AWS 계정의 총 비용을 분석하고 절감 방안 제시\n");
                prompt.append("2. 계정별 비용 분석: 특정 계정의 비용을 상세 분석\n");
                prompt.append("3. 서비스별 분석: EC2, S3, RDS 등 특정 서비스의 비용 최적화\n");
                prompt.append("4. 리소스 최적화: 현재 실행 중인 EC2 인스턴스의 크기/타입 최적화 제안\n");
                prompt.append("5. 미사용 리소스 식별: 장기간 중지된 인스턴스나 사용하지 않는 리소스 식별\n\n");
                
            } else {
                prompt.append("현재 활성화된 AWS 계정이 없습니다.\n");
                prompt.append("계정을 연결하면 실제 비용 데이터를 기반으로 최적화 조언을 제공할 수 있습니다.\n\n");
            }
        } catch (Exception e) {
            log.error("Failed to build resource and cost information", e);
            prompt.append("리소스 및 비용 정보를 불러오지 못했습니다. 규칙 기반 답변을 제공합니다.\n\n");
        }
        
        prompt.append("사용자의 질문에 친절하고 전문적으로 답변하세요. ");
        prompt.append("위의 비용 정보와 리소스 정보를 참고하여 구체적이고 실용적인 최적화 조언을 제시하세요. ");
        prompt.append("사용자가 특정 서비스나 계정에 대해 질문하면, 해당 정보를 활용하여 답변하세요. ");
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

