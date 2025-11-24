package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AlertCondition;
import com.budgetops.backend.aws.dto.AlertRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * AWS EC2 알림 규칙 로더
 * csp_aws_ec2.yml 파일을 로드하여 알림 규칙 정보 제공
 */
@Slf4j
@Component
public class AwsEc2RuleLoader {
    
    private final List<AlertRule> alertRules = new ArrayList<>();
    private final Yaml yaml = new Yaml();
    
    public AwsEc2RuleLoader() {
        loadRules();
    }
    
    private void loadRules() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("classpath:costs/csp_aws_ec2.yml");
            
            if (!resource.exists()) {
                log.warn("AWS EC2 rule file not found: csp_aws_ec2.yml");
                return;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> data = yaml.load(inputStream);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rulesList = (List<Map<String, Object>>) data.get("rules");
                
                if (rulesList != null) {
                    for (Map<String, Object> ruleMap : rulesList) {
                        AlertRule rule = parseRule(ruleMap);
                        if (rule != null) {
                            alertRules.add(rule);
                        }
                    }
                    
                    log.info("Loaded {} alert rules for AWS EC2", alertRules.size());
                }
            }
        } catch (Exception e) {
            log.error("Failed to load AWS EC2 alert rules", e);
        }
    }
    
    private AlertRule parseRule(Map<String, Object> ruleMap) {
        try {
            String id = (String) ruleMap.get("id");
            String title = (String) ruleMap.get("title");
            String description = (String) ruleMap.get("description");
            String recommendation = (String) ruleMap.get("recommendation");
            String costSaving = (String) ruleMap.get("cost_saving");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conditionsList = (List<Map<String, Object>>) ruleMap.get("conditions");
            
            List<AlertCondition> conditions = new ArrayList<>();
            if (conditionsList != null) {
                for (Map<String, Object> conditionMap : conditionsList) {
                    AlertCondition condition = parseCondition(conditionMap);
                    if (condition != null) {
                        conditions.add(condition);
                    }
                }
            }
            
            return AlertRule.builder()
                    .id(id)
                    .title(title)
                    .description(description)
                    .conditions(conditions)
                    .recommendation(recommendation)
                    .costSaving(costSaving)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse alert rule: {}", ruleMap, e);
            return null;
        }
    }
    
    private AlertCondition parseCondition(Map<String, Object> conditionMap) {
        try {
            String metric = (String) conditionMap.get("metric");
            Object threshold = conditionMap.get("threshold");
            String period = (String) conditionMap.get("period");
            String value = (String) conditionMap.get("value");
            
            return AlertCondition.builder()
                    .metric(metric)
                    .threshold(threshold)
                    .period(period)
                    .value(value)
                    .operator("<") // 기본값은 미만
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse condition: {}", conditionMap, e);
            return null;
        }
    }
    
    /**
     * 모든 알림 규칙 조회
     */
    public List<AlertRule> getAllRules() {
        return new ArrayList<>(alertRules);
    }
    
    /**
     * 특정 규칙 ID로 규칙 조회
     */
    public AlertRule getRuleById(String ruleId) {
        return alertRules.stream()
                .filter(rule -> rule.getId().equals(ruleId))
                .findFirst()
                .orElse(null);
    }
}

