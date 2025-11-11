package com.budgetops.backend.costs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Component
public class CostOptimizationRuleLoader {
    
    private final Map<String, List<OptimizationRule>> rulesByCspAndService = new HashMap<>();
    private final Yaml yaml = new Yaml();
    
    public CostOptimizationRuleLoader() {
        loadRules();
    }
    
    private void loadRules() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:costs/csp_*.yml");
            
            log.info("Loading optimization rules from {} files", resources.length);
            
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(inputStream);
                    
                    String csp = (String) data.get("csp");
                    String service = (String) data.get("service");
                    String name = (String) data.get("name");
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rulesList = (List<Map<String, Object>>) data.get("rules");
                    
                    if (rulesList != null) {
                        List<OptimizationRule> rules = new ArrayList<>();
                        for (Map<String, Object> ruleMap : rulesList) {
                            OptimizationRule rule = OptimizationRule.fromMap(ruleMap);
                            rules.add(rule);
                        }
                        
                        String key = csp + "_" + service;
                        rulesByCspAndService.put(key, rules);
                        log.info("Loaded {} rules for {} - {}", rules.size(), csp, service);
                    }
                } catch (Exception e) {
                    log.error("Failed to load rule file: {}", resource.getFilename(), e);
                }
            }
            
            log.info("Total loaded rule sets: {}", rulesByCspAndService.size());
        } catch (Exception e) {
            log.error("Failed to load optimization rules", e);
        }
    }
    
    public List<OptimizationRule> getRules(String csp, String service) {
        String key = csp + "_" + service;
        return rulesByCspAndService.getOrDefault(key, Collections.emptyList());
    }
    
    public List<OptimizationRule> getAllRules() {
        return rulesByCspAndService.values().stream()
                .flatMap(List::stream)
                .toList();
    }
    
    public String formatRulesForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 클라우드 비용 최적화 규칙\n\n");
        
        for (Map.Entry<String, List<OptimizationRule>> entry : rulesByCspAndService.entrySet()) {
            String[] parts = entry.getKey().split("_", 2);
            String csp = parts[0];
            String service = parts.length > 1 ? parts[1] : "";
            
            sb.append("## ").append(csp).append(" - ").append(service).append("\n\n");
            
            for (OptimizationRule rule : entry.getValue()) {
                sb.append("### ").append(rule.getTitle()).append("\n");
                sb.append("- **설명**: ").append(rule.getDescription()).append("\n");
                sb.append("- **조건**: ").append(rule.getConditionsDescription()).append("\n");
                sb.append("- **권장사항**: ").append(rule.getRecommendation()).append("\n");
                sb.append("- **예상 절감액**: ").append(rule.getCostSaving()).append("\n\n");
            }
        }
        
        return sb.toString();
    }
    
    public static class OptimizationRule {
        private String id;
        private String title;
        private String description;
        private String conditionsDescription;
        private String recommendation;
        private String costSaving;
        
        public static OptimizationRule fromMap(Map<String, Object> map) {
            OptimizationRule rule = new OptimizationRule();
            rule.id = (String) map.get("id");
            rule.title = (String) map.get("title");
            rule.description = (String) map.get("description");
            rule.recommendation = (String) map.get("recommendation");
            rule.costSaving = (String) map.get("cost_saving");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) map.get("conditions");
            if (conditions != null) {
                rule.conditionsDescription = formatConditions(conditions);
            }
            
            return rule;
        }
        
        private static String formatConditions(List<Map<String, Object>> conditions) {
            List<String> conditionStrs = new ArrayList<>();
            for (Map<String, Object> condition : conditions) {
                String metric = (String) condition.get("metric");
                Object threshold = condition.get("threshold");
                Object period = condition.get("period");
                
                StringBuilder cond = new StringBuilder(metric);
                if (threshold != null) {
                    cond.append(" < ").append(threshold);
                }
                if (period != null) {
                    cond.append(" (").append(period).append(")");
                }
                conditionStrs.add(cond.toString());
            }
            return String.join(", ", conditionStrs);
        }
        
        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getConditionsDescription() { return conditionsDescription; }
        public String getRecommendation() { return recommendation; }
        public String getCostSaving() { return costSaving; }
    }
}

