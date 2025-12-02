package com.budgetops.backend.ai.service;

import com.budgetops.backend.costs.CostOptimizationRuleLoader;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 룰-리소스 매칭 서비스
 * 최적화 규칙과 실제 리소스를 매칭하여 적용 가능한 규칙을 식별합니다.
 */
@Slf4j
@Service
public class RuleResourceMatcher {

    private final CostOptimizationRuleLoader ruleLoader;

    public RuleResourceMatcher(CostOptimizationRuleLoader ruleLoader) {
        this.ruleLoader = ruleLoader;
    }

    /**
     * 리소스 분석 결과와 규칙을 매칭하여 적용 가능한 규칙을 반환합니다.
     */
    public List<MatchedRule> matchRules(ResourceAnalysisService.ResourceAnalysisResult analysis) {
        List<MatchedRule> matchedRules = new ArrayList<>();

        // AWS EC2 규칙 매칭
        List<CostOptimizationRuleLoader.OptimizationRule> awsEc2Rules = ruleLoader.getRules("AWS", "EC2");
        for (CostOptimizationRuleLoader.OptimizationRule rule : awsEc2Rules) {
            for (Map.Entry<String, Map<String, List<com.budgetops.backend.aws.dto.AwsEc2InstanceResponse>>> accountEntry : 
                    analysis.getAwsResources().entrySet()) {
                String accountName = accountEntry.getKey();
                for (Map.Entry<String, List<com.budgetops.backend.aws.dto.AwsEc2InstanceResponse>> regionEntry : 
                        accountEntry.getValue().entrySet()) {
                    String region = regionEntry.getKey();
                    for (com.budgetops.backend.aws.dto.AwsEc2InstanceResponse instance : regionEntry.getValue()) {
                        if (isRuleApplicable(rule, instance)) {
                            matchedRules.add(MatchedRule.builder()
                                    .rule(rule)
                                    .csp("AWS")
                                    .service("EC2")
                                    .accountName(accountName)
                                    .resourceId(instance.getInstanceId())
                                    .resourceName(instance.getName())
                                    .region(region)
                                    .build());
                        }
                    }
                }
            }
        }

        // Azure VM 규칙 매칭
        List<CostOptimizationRuleLoader.OptimizationRule> azureVmRules = ruleLoader.getRules("Azure", "Virtual Machines");
        for (CostOptimizationRuleLoader.OptimizationRule rule : azureVmRules) {
            for (Map.Entry<String, List<com.budgetops.backend.azure.dto.AzureVirtualMachineResponse>> accountEntry : 
                    analysis.getAzureResources().entrySet()) {
                String accountName = accountEntry.getKey();
                for (com.budgetops.backend.azure.dto.AzureVirtualMachineResponse vm : accountEntry.getValue()) {
                    if (isRuleApplicable(rule, vm)) {
                        matchedRules.add(MatchedRule.builder()
                                .rule(rule)
                                .csp("Azure")
                                .service("Virtual Machines")
                                .accountName(accountName)
                                .resourceId(vm.getName())
                                .resourceName(vm.getName())
                                .build());
                    }
                }
            }
        }

        // GCP Compute Engine 규칙 매칭
        List<CostOptimizationRuleLoader.OptimizationRule> gcpComputeRules = ruleLoader.getRules("GCP", "Compute Engine");
        for (CostOptimizationRuleLoader.OptimizationRule rule : gcpComputeRules) {
            for (Map.Entry<String, List<com.budgetops.backend.gcp.dto.GcpResourceResponse>> accountEntry : 
                    analysis.getGcpResources().entrySet()) {
                String accountName = accountEntry.getKey();
                for (com.budgetops.backend.gcp.dto.GcpResourceResponse resource : accountEntry.getValue()) {
                    if ("compute.googleapis.com/Instance".equals(resource.getResourceType()) && 
                        isRuleApplicable(rule, resource)) {
                        matchedRules.add(MatchedRule.builder()
                                .rule(rule)
                                .csp("GCP")
                                .service("Compute Engine")
                                .accountName(accountName)
                                .resourceId(resource.getResourceId())
                                .resourceName(resource.getResourceName())
                                .build());
                    }
                }
            }
        }

        // NCP Server 규칙 매칭
        List<CostOptimizationRuleLoader.OptimizationRule> ncpServerRules = ruleLoader.getRules("NCP", "Server");
        for (CostOptimizationRuleLoader.OptimizationRule rule : ncpServerRules) {
            for (Map.Entry<String, Map<String, List<com.budgetops.backend.ncp.dto.NcpServerInstanceResponse>>> accountEntry : 
                    analysis.getNcpResources().entrySet()) {
                String accountName = accountEntry.getKey();
                for (Map.Entry<String, List<com.budgetops.backend.ncp.dto.NcpServerInstanceResponse>> regionEntry : 
                        accountEntry.getValue().entrySet()) {
                    String region = regionEntry.getKey();
                    for (com.budgetops.backend.ncp.dto.NcpServerInstanceResponse server : regionEntry.getValue()) {
                        if (isRuleApplicable(rule, server)) {
                            matchedRules.add(MatchedRule.builder()
                                    .rule(rule)
                                    .csp("NCP")
                                    .service("Server")
                                    .accountName(accountName)
                                    .resourceId(server.getServerInstanceNo())
                                    .resourceName(server.getServerName())
                                    .region(region)
                                    .build());
                        }
                    }
                }
            }
        }

        return matchedRules;
    }

    /**
     * 규칙이 리소스에 적용 가능한지 확인합니다.
     * 현재는 기본적으로 적용 가능하다고 가정합니다 (실제 메트릭 기반 매칭은 향후 구현).
     */
    private boolean isRuleApplicable(CostOptimizationRuleLoader.OptimizationRule rule, Object resource) {
        // TODO: 실제 메트릭 데이터를 기반으로 규칙 조건을 평가
        // 현재는 모든 규칙이 적용 가능하다고 가정
        return true;
    }

    @Value
    @Builder
    public static class MatchedRule {
        CostOptimizationRuleLoader.OptimizationRule rule;
        String csp;
        String service;
        String accountName;
        String resourceId;
        String resourceName;
        String region;
    }
}

