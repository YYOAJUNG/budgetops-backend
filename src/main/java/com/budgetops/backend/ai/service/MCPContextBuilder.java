package com.budgetops.backend.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP (Model Context Protocol) 컨텍스트 빌더
 * 리소스 분석 결과와 매칭된 규칙을 기반으로 MCP 형식의 컨텍스트를 생성합니다.
 */
@Slf4j
@Service
public class MCPContextBuilder {

    private final ResourceAnalysisService resourceAnalysisService;
    private final RuleResourceMatcher ruleResourceMatcher;

    public MCPContextBuilder(
            ResourceAnalysisService resourceAnalysisService,
            RuleResourceMatcher ruleResourceMatcher) {
        this.resourceAnalysisService = resourceAnalysisService;
        this.ruleResourceMatcher = ruleResourceMatcher;
    }

    /**
     * MCP 컨텍스트를 생성합니다.
     */
    public MCPContext buildContext(Long memberId) {
        // 리소스 분석
        ResourceAnalysisService.ResourceAnalysisResult analysis = 
                resourceAnalysisService.analyzeAllResources(memberId);

        // 규칙-리소스 매칭
        List<RuleResourceMatcher.MatchedRule> matchedRules = 
                ruleResourceMatcher.matchRules(analysis);

        // MCP 컨텍스트 생성
        return new MCPContext(
                formatResources(analysis),
                formatMatchedRules(matchedRules),
                formatOptimizationOpportunities(matchedRules)
        );
    }

    /**
     * 리소스 정보를 MCP 형식으로 포맷합니다.
     */
    private String formatResources(ResourceAnalysisService.ResourceAnalysisResult analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 클라우드 리소스 현황 ===\n\n");

        // AWS 리소스
        if (!analysis.getAwsResources().isEmpty()) {
            sb.append("📊 AWS EC2:\n");
            for (Map.Entry<String, Map<String, List<com.budgetops.backend.aws.dto.AwsEc2InstanceResponse>>> accountEntry : 
                    analysis.getAwsResources().entrySet()) {
                String accountName = accountEntry.getKey();
                Map<String, List<com.budgetops.backend.aws.dto.AwsEc2InstanceResponse>> regions = accountEntry.getValue();
                
                int totalInstances = regions.values().stream().mapToInt(List::size).sum();
                long runningCount = regions.values().stream()
                        .flatMap(List::stream)
                        .filter(i -> "running".equalsIgnoreCase(i.getState()))
                        .count();
                
                sb.append(String.format("- 계정: %s, 총 %d대 (실행중: %d대)\n", 
                        accountName, totalInstances, runningCount));
            }
            sb.append("\n");
        }

        // Azure 리소스
        if (!analysis.getAzureResources().isEmpty()) {
            sb.append("📊 Azure Virtual Machines:\n");
            for (Map.Entry<String, List<com.budgetops.backend.azure.dto.AzureVirtualMachineResponse>> accountEntry : 
                    analysis.getAzureResources().entrySet()) {
                String accountName = accountEntry.getKey();
                List<com.budgetops.backend.azure.dto.AzureVirtualMachineResponse> vms = accountEntry.getValue();
                
                long runningCount = vms.stream()
                        .filter(vm -> "running".equalsIgnoreCase(vm.getPowerState()))
                        .count();
                
                sb.append(String.format("- 계정: %s, 총 %d대 (실행중: %d대)\n", 
                        accountName, vms.size(), runningCount));
            }
            sb.append("\n");
        }

        // GCP 리소스
        if (!analysis.getGcpResources().isEmpty()) {
            sb.append("📊 GCP Compute Engine:\n");
            for (Map.Entry<String, List<com.budgetops.backend.gcp.dto.GcpResourceResponse>> accountEntry : 
                    analysis.getGcpResources().entrySet()) {
                String accountName = accountEntry.getKey();
                List<com.budgetops.backend.gcp.dto.GcpResourceResponse> resources = accountEntry.getValue();
                
                long runningCount = resources.stream()
                        .filter(r -> "RUNNING".equalsIgnoreCase(r.getStatus()) || "running".equalsIgnoreCase(r.getStatus()))
                        .count();
                
                sb.append(String.format("- 계정: %s, 총 %d개 (실행중: %d개)\n", 
                        accountName, resources.size(), runningCount));
            }
            sb.append("\n");
        }

        // NCP 리소스
        if (!analysis.getNcpResources().isEmpty()) {
            sb.append("📊 NCP Server:\n");
            for (Map.Entry<String, Map<String, List<com.budgetops.backend.ncp.dto.NcpServerInstanceResponse>>> accountEntry : 
                    analysis.getNcpResources().entrySet()) {
                String accountName = accountEntry.getKey();
                Map<String, List<com.budgetops.backend.ncp.dto.NcpServerInstanceResponse>> regions = accountEntry.getValue();
                
                int totalServers = regions.values().stream().mapToInt(List::size).sum();
                long runningCount = regions.values().stream()
                        .flatMap(List::stream)
                        .filter(s -> "running".equalsIgnoreCase(s.getServerInstanceStatus()))
                        .count();
                
                sb.append(String.format("- 계정: %s, 총 %d대 (실행중: %d대)\n", 
                        accountName, totalServers, runningCount));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 매칭된 규칙을 포맷합니다.
     */
    private String formatMatchedRules(List<RuleResourceMatcher.MatchedRule> matchedRules) {
        if (matchedRules.isEmpty()) {
            return "현재 적용 가능한 최적화 규칙이 없습니다.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 적용 가능한 최적화 규칙 ===\n\n");

        // CSP별로 그룹화
        Map<String, List<RuleResourceMatcher.MatchedRule>> rulesByCsp = matchedRules.stream()
                .collect(Collectors.groupingBy(RuleResourceMatcher.MatchedRule::getCsp));

        for (Map.Entry<String, List<RuleResourceMatcher.MatchedRule>> entry : rulesByCsp.entrySet()) {
            String csp = entry.getKey();
            List<RuleResourceMatcher.MatchedRule> rules = entry.getValue();

            sb.append(String.format("📋 %s:\n", csp));
            for (RuleResourceMatcher.MatchedRule matchedRule : rules) {
                sb.append(String.format("- [%s] %s\n", 
                        matchedRule.getRule().getTitle(), 
                        matchedRule.getResourceName() != null ? matchedRule.getResourceName() : matchedRule.getResourceId()));
                sb.append(String.format("  → %s\n", matchedRule.getRule().getRecommendation()));
                sb.append(String.format("  → 예상 절감액: %s\n", matchedRule.getRule().getCostSaving()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 최적화 기회를 포맷합니다.
     */
    private String formatOptimizationOpportunities(List<RuleResourceMatcher.MatchedRule> matchedRules) {
        if (matchedRules.isEmpty()) {
            return "현재 식별된 최적화 기회가 없습니다.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 최적화 기회 요약 ===\n\n");

        // 규칙별로 그룹화하여 통계 생성
        Map<String, Long> ruleCounts = matchedRules.stream()
                .collect(Collectors.groupingBy(
                        mr -> mr.getRule().getTitle(),
                        Collectors.counting()
                ));

        sb.append("규칙별 적용 가능한 리소스 수:\n");
        for (Map.Entry<String, Long> entry : ruleCounts.entrySet()) {
            sb.append(String.format("- %s: %d개 리소스\n", entry.getKey(), entry.getValue()));
        }
        sb.append("\n");

        // CSP별 최적화 기회
        Map<String, Long> cspCounts = matchedRules.stream()
                .collect(Collectors.groupingBy(
                        RuleResourceMatcher.MatchedRule::getCsp,
                        Collectors.counting()
                ));

        sb.append("CSP별 최적화 기회:\n");
        for (Map.Entry<String, Long> entry : cspCounts.entrySet()) {
            sb.append(String.format("- %s: %d개 기회\n", entry.getKey(), entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * MCP 컨텍스트를 프롬프트 형식으로 변환합니다.
     */
    public String formatContextForPrompt(MCPContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getResources());
        sb.append("\n");
        sb.append(context.getMatchedRules());
        sb.append("\n");
        sb.append(context.getOptimizationOpportunities());
        return sb.toString();
    }

    public static class MCPContext {
        private final String resources;
        private final String matchedRules;
        private final String optimizationOpportunities;

        public MCPContext(String resources, String matchedRules, String optimizationOpportunities) {
            this.resources = resources;
            this.matchedRules = matchedRules;
            this.optimizationOpportunities = optimizationOpportunities;
        }

        public String getResources() {
            return resources;
        }

        public String getMatchedRules() {
            return matchedRules;
        }

        public String getOptimizationOpportunities() {
            return optimizationOpportunities;
        }
    }
}

