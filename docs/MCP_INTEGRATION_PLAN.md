# MCP (Model Context Protocol) 통합 방안

## 개요

현재 BudgetOps AI 어시스턴트는 단순 텍스트 프롬프트로 컨텍스트를 제공하고 있습니다. MCP를 통합하여 구조화된 컨텍스트를 제공함으로써 더 정확하고 일관된 AI 응답을 생성할 수 있습니다.

## MCP란?

Model Context Protocol (MCP)는 LLM에 구조화된 컨텍스트를 제공하는 프로토콜입니다. 단순 텍스트가 아닌 JSON 기반의 구조화된 데이터를 통해:
- 리소스 정보를 명확하게 전달
- 메트릭 데이터를 표준화된 형식으로 제공
- 룰과 리소스의 매칭을 명시적으로 표현
- AI가 더 정확하게 분석할 수 있도록 지원

## 현재 구조 vs MCP 통합 후 구조

### 현재 구조
```
System Prompt (텍스트)
├── 룰 목록 (마크다운 텍스트)
├── 리소스 현황 (텍스트 요약)
└── 비용 정보 (텍스트 요약)
```

### MCP 통합 후 구조
```
System Instruction (텍스트)
└── MCP Context (JSON)
    ├── Rules (구조화된 룰)
    ├── Resources (구조화된 리소스 데이터)
    │   ├── AWS EC2 Instances
    │   ├── Azure VMs
    │   ├── GCP Resources
    │   └── NCP Servers
    ├── Metrics (구조화된 메트릭 데이터)
    ├── Cost Data (구조화된 비용 데이터)
    └── Optimization Opportunities (매칭된 최적화 기회)
```

## 구현 방안

### 1. MCP Context Builder 서비스 생성

```java
@Service
public class MCPContextBuilder {
    
    /**
     * 리소스 분석 결과를 MCP 형식의 JSON 컨텍스트로 변환
     */
    public Map<String, Object> buildMCPContext(
            ResourceAnalysisService.ResourceAnalysisResult analysis,
            List<CostOptimizationRuleLoader.OptimizationRule> rules,
            Map<String, Object> costData
    ) {
        Map<String, Object> context = new HashMap<>();
        
        // 1. Rules 섹션
        context.put("rules", buildRulesContext(rules));
        
        // 2. Resources 섹션
        context.put("resources", buildResourcesContext(analysis));
        
        // 3. Metrics 섹션 (필요시)
        context.put("metrics", buildMetricsContext(analysis));
        
        // 4. Cost Data 섹션
        context.put("costs", costData);
        
        // 5. Optimization Opportunities 섹션
        context.put("optimization_opportunities", 
                matchRulesToResources(rules, analysis));
        
        return context;
    }
    
    private List<Map<String, Object>> buildRulesContext(
            List<CostOptimizationRuleLoader.OptimizationRule> rules) {
        return rules.stream().map(rule -> {
            Map<String, Object> ruleMap = new HashMap<>();
            ruleMap.put("id", rule.getId());
            ruleMap.put("csp", extractCsp(rule));
            ruleMap.put("service", extractService(rule));
            ruleMap.put("title", rule.getTitle());
            ruleMap.put("description", rule.getDescription());
            ruleMap.put("conditions", parseConditions(rule.getConditionsDescription()));
            ruleMap.put("recommendation", rule.getRecommendation());
            ruleMap.put("cost_saving", rule.getCostSaving());
            return ruleMap;
        }).collect(Collectors.toList());
    }
    
    private Map<String, Object> buildResourcesContext(
            ResourceAnalysisService.ResourceAnalysisResult analysis) {
        Map<String, Object> resources = new HashMap<>();
        
        // AWS Resources
        List<Map<String, Object>> awsResources = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<AwsEc2InstanceResponse>>> accountEntry 
                : analysis.awsResources.entrySet()) {
            for (Map.Entry<String, List<AwsEc2InstanceResponse>> regionEntry 
                    : accountEntry.getValue().entrySet()) {
                for (AwsEc2InstanceResponse instance : regionEntry.getValue()) {
                    Map<String, Object> resource = new HashMap<>();
                    resource.put("provider", "AWS");
                    resource.put("type", "EC2");
                    resource.put("account", accountEntry.getKey());
                    resource.put("region", regionEntry.getKey());
                    resource.put("id", instance.getInstanceId());
                    resource.put("name", instance.getName());
                    resource.put("instance_type", instance.getInstanceType());
                    resource.put("state", instance.getState());
                    resource.put("launch_time", instance.getLaunchTime());
                    awsResources.add(resource);
                }
            }
        }
        resources.put("aws", awsResources);
        
        // Azure Resources
        List<Map<String, Object>> azureResources = new ArrayList<>();
        for (Map.Entry<String, List<AzureVirtualMachineResponse>> accountEntry 
                : analysis.azureResources.entrySet()) {
            for (AzureVirtualMachineResponse vm : accountEntry.getValue()) {
                Map<String, Object> resource = new HashMap<>();
                resource.put("provider", "Azure");
                resource.put("type", "VirtualMachine");
                resource.put("account", accountEntry.getKey());
                resource.put("id", vm.getId());
                resource.put("name", vm.getName());
                resource.put("vm_size", vm.getVmSize());
                resource.put("power_state", vm.getPowerState());
                resource.put("location", vm.getLocation());
                azureResources.add(resource);
            }
        }
        resources.put("azure", azureResources);
        
        // GCP Resources
        List<Map<String, Object>> gcpResources = new ArrayList<>();
        for (Map.Entry<String, List<GcpResourceResponse>> accountEntry 
                : analysis.gcpResources.entrySet()) {
            for (GcpResourceResponse resource : accountEntry.getValue()) {
                Map<String, Object> resourceMap = new HashMap<>();
                resourceMap.put("provider", "GCP");
                resourceMap.put("type", resource.getResourceTypeShort());
                resourceMap.put("account", accountEntry.getKey());
                resourceMap.put("id", resource.getResourceId());
                resourceMap.put("name", resource.getResourceName());
                resourceMap.put("status", resource.getStatus());
                resourceMap.put("region", resource.getRegion());
                gcpResources.add(resourceMap);
            }
        }
        resources.put("gcp", gcpResources);
        
        // NCP Resources
        List<Map<String, Object>> ncpResources = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<NcpServerInstanceResponse>>> accountEntry 
                : analysis.ncpResources.entrySet()) {
            for (Map.Entry<String, List<NcpServerInstanceResponse>> regionEntry 
                    : accountEntry.getValue().entrySet()) {
                for (NcpServerInstanceResponse server : regionEntry.getValue()) {
                    Map<String, Object> resource = new HashMap<>();
                    resource.put("provider", "NCP");
                    resource.put("type", "Server");
                    resource.put("account", accountEntry.getKey());
                    resource.put("region", regionEntry.getKey());
                    resource.put("id", server.getServerInstanceNo());
                    resource.put("name", server.getServerName());
                    resource.put("cpu_count", server.getCpuCount());
                    resource.put("memory_size", server.getMemorySize());
                    resource.put("status", server.getServerInstanceStatus());
                    ncpResources.add(resource);
                }
            }
        }
        resources.put("ncp", ncpResources);
        
        return resources;
    }
    
    /**
     * 룰과 리소스를 매칭하여 최적화 기회 식별
     */
    private List<Map<String, Object>> matchRulesToResources(
            List<CostOptimizationRuleLoader.OptimizationRule> rules,
            ResourceAnalysisService.ResourceAnalysisResult analysis) {
        List<Map<String, Object>> opportunities = new ArrayList<>();
        
        // 각 룰에 대해 리소스를 매칭
        for (CostOptimizationRuleLoader.OptimizationRule rule : rules) {
            List<Map<String, Object>> matchedResources = findMatchingResources(rule, analysis);
            if (!matchedResources.isEmpty()) {
                Map<String, Object> opportunity = new HashMap<>();
                opportunity.put("rule_id", rule.getId());
                opportunity.put("rule_title", rule.getTitle());
                opportunity.put("matched_resources", matchedResources);
                opportunity.put("recommendation", rule.getRecommendation());
                opportunity.put("cost_saving", rule.getCostSaving());
                opportunities.add(opportunity);
            }
        }
        
        return opportunities;
    }
    
    private List<Map<String, Object>> findMatchingResources(
            CostOptimizationRuleLoader.OptimizationRule rule,
            ResourceAnalysisService.ResourceAnalysisResult analysis) {
        // 룰의 조건에 맞는 리소스 찾기
        // 예: CPU 사용률이 40% 미만인 리소스 찾기
        // 실제 구현 시 메트릭 데이터를 조회하여 매칭
        return new ArrayList<>();
    }
}
```

### 2. AIChatService에 MCP 통합

```java
private String buildSystemPrompt() {
    StringBuilder prompt = new StringBuilder();
    prompt.append("당신은 BudgetOps의 클라우드 비용 최적화 전문 AI 어시스턴트입니다.\n\n");
    
    // 리소스 분석 수행
    ResourceAnalysisService.ResourceAnalysisResult analysis = 
            resourceAnalysisService.analyzeAllResources(getCurrentMemberId());
    
    // MCP 컨텍스트 생성
    Map<String, Object> mcpContext = mcpContextBuilder.buildMCPContext(
            analysis,
            ruleLoader.getAllRules(),
            getCostData()
    );
    
    // MCP 컨텍스트를 JSON 문자열로 변환하여 프롬프트에 포함
    try {
        ObjectMapper objectMapper = new ObjectMapper();
        String mcpContextJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(mcpContext);
        
        prompt.append("=== 구조화된 리소스 및 룰 컨텍스트 (MCP 형식) ===\n");
        prompt.append("다음 JSON 데이터는 실제 클라우드 리소스와 최적화 룰을 구조화한 것입니다.\n");
        prompt.append("이 데이터를 기반으로 구체적인 최적화 권고를 제시하세요.\n\n");
        prompt.append(mcpContextJson);
        prompt.append("\n\n");
    } catch (Exception e) {
        log.error("Failed to build MCP context", e);
        // 실패 시 기존 방식으로 fallback
        prompt.append(ruleLoader.formatRulesForPrompt());
        prompt.append("\n");
        prompt.append(resourceAnalysisService.formatResourceAnalysisForPrompt(analysis));
    }
    
    // 답변 가이드라인
    prompt.append("=== 답변 작성 가이드라인 ===\n\n");
    prompt.append("위의 구조화된 데이터를 활용하여:\n");
    prompt.append("1. 'optimization_opportunities' 섹션의 매칭된 리소스를 기반으로 구체적인 권고를 제시하세요.\n");
    prompt.append("2. '~하기 때문에 ~하세요' 형식으로 답변하세요.\n");
    prompt.append("3. 리소스 ID, 이름, 현재 상태 등 구체적인 정보를 포함하세요.\n");
    
    return prompt.toString();
}
```

### 3. Gemini API에 MCP 컨텍스트 전달

Gemini 2.5 Flash는 Function Calling과 구조화된 데이터를 지원하므로, MCP 컨텍스트를 별도 파트로 전달할 수 있습니다:

```java
private String callGeminiAPI(String systemPrompt, Map<String, Object> mcpContext, 
                            List<Map<String, String>> history) {
    Map<String, Object> requestBody = new HashMap<>();
    
    // System Instruction
    Map<String, Object> systemInstruction = new HashMap<>();
    List<Map<String, Object>> systemParts = new ArrayList<>();
    
    // 텍스트 프롬프트
    Map<String, String> textPart = new HashMap<>();
    textPart.put("text", systemPrompt);
    systemParts.add(textPart);
    
    // MCP 컨텍스트를 JSON 파트로 추가 (Gemini가 구조화된 데이터를 이해할 수 있도록)
    try {
        ObjectMapper objectMapper = new ObjectMapper();
        String mcpContextJson = objectMapper.writeValueAsString(mcpContext);
        
        // Gemini는 JSON을 텍스트로 받아서 파싱하므로, 명시적으로 JSON임을 표시
        Map<String, String> jsonPart = new HashMap<>();
        jsonPart.put("text", "STRUCTURED_CONTEXT_JSON:\n" + mcpContextJson);
        systemParts.add(jsonPart);
    } catch (Exception e) {
        log.warn("Failed to add MCP context as JSON part", e);
    }
    
    systemInstruction.put("parts", systemParts);
    requestBody.put("systemInstruction", systemInstruction);
    
    // ... 나머지 코드
}
```

## 단계별 구현 계획

### Phase 1: MCP Context Builder 구현
1. `MCPContextBuilder` 서비스 생성
2. 리소스 데이터를 MCP 형식으로 변환
3. 룰 데이터를 MCP 형식으로 변환

### Phase 2: 룰-리소스 매칭 로직 구현
1. 룰의 조건을 파싱하는 로직 구현
2. 리소스 메트릭 데이터 조회 (필요시)
3. 조건에 맞는 리소스 매칭 알고리즘 구현

### Phase 3: AIChatService 통합
1. `MCPContextBuilder`를 `AIChatService`에 주입
2. `buildSystemPrompt`에서 MCP 컨텍스트 생성 및 포함
3. 기존 텍스트 기반 방식과 병행하여 테스트

### Phase 4: 최적화 및 검증
1. MCP 컨텍스트 크기 최적화 (너무 크면 토큰 제한 초과 가능)
2. AI 응답 품질 비교 (텍스트 vs MCP)
3. 필요시 하이브리드 방식 채택 (중요한 데이터만 MCP로, 나머지는 텍스트로)

## 장점

1. **구조화된 데이터**: AI가 리소스 정보를 더 정확하게 이해
2. **명시적 매칭**: 룰과 리소스의 매칭을 명시적으로 표현
3. **확장성**: 새로운 CSP나 리소스 타입 추가 시 쉽게 확장 가능
4. **일관성**: 구조화된 형식으로 일관된 컨텍스트 제공

## 주의사항

1. **토큰 제한**: MCP 컨텍스트가 너무 크면 Gemini의 토큰 제한에 걸릴 수 있음
2. **메트릭 데이터**: 실시간 메트릭 조회는 API 호출이 많아질 수 있음
3. **점진적 도입**: 기존 텍스트 방식과 병행하여 점진적으로 전환 권장

## 참고 자료

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [Gemini API Function Calling](https://ai.google.dev/docs/function_calling)
- [Gemini Structured Data](https://ai.google.dev/docs/structured_data)

