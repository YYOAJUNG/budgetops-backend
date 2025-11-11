package com.budgetops.backend.simulator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * UCAS 최적화 룰 로더
 * ucas_*.yml 파일을 로드하여 액션 타입별 룰 정보 제공
 */
@Slf4j
@Component
public class UcasRuleLoader {
    
    private final Map<String, UcasRule> rulesByActionType = new HashMap<>();
    private final Yaml yaml = new Yaml();
    
    public UcasRuleLoader() {
        loadRules();
    }
    
    private void loadRules() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:costs/ucas_*.yml");
            
            log.info("Loading UCAS rules from {} files", resources.length);
            
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(inputStream);
                    
                    String ruleId = (String) data.get("rule_id");
                    String actionType = (String) data.get("action");
                    String scope = (String) data.get("scope");
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> match = (Map<String, Object>) data.get("match");
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) data.get("params");
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> estimate = (Map<String, Object>) data.get("estimate");
                    
                    UcasRule rule = UcasRule.builder()
                            .ruleId(ruleId)
                            .actionType(actionType)
                            .scope(scope)
                            .match(match != null ? match : Collections.emptyMap())
                            .params(params != null ? params : Collections.emptyMap())
                            .estimateFormula(estimate != null ? (String) estimate.get("formula") : null)
                            .approvalRequired(estimate != null && Boolean.TRUE.equals(estimate.get("approval")))
                            .build();
                    
                    rulesByActionType.put(actionType, rule);
                    log.info("Loaded UCAS rule: {} for action type: {}", ruleId, actionType);
                } catch (Exception e) {
                    log.error("Failed to load UCAS rule file: {}", resource.getFilename(), e);
                }
            }
            
            log.info("Total loaded UCAS rules: {}", rulesByActionType.size());
        } catch (Exception e) {
            log.error("Failed to load UCAS rules", e);
        }
    }
    
    public UcasRule getRule(String actionType) {
        return rulesByActionType.get(actionType);
    }
    
    public Map<String, UcasRule> getAllRules() {
        return Collections.unmodifiableMap(rulesByActionType);
    }
    
    /**
     * 룰 기반 근거 설명 생성
     */
    public String generateBasisDescription(String actionType, Double savings, Map<String, Object> params) {
        UcasRule rule = getRule(actionType);
        if (rule == null) {
            return "최적화 규칙에 따라 비용 절감이 가능합니다.";
        }
        
        StringBuilder basis = new StringBuilder();
        
        // 액션 타입별 상세 설명
        switch (actionType) {
            case "offhours":
                basis.append("비업무 시간 인스턴스 중단 규칙 적용:\n");
                basis.append("• 적용 조건: ");
                if (rule.getMatch().containsKey("tags.env")) {
                    basis.append("개발 환경 태그가 있는 인스턴스, ");
                }
                if (rule.getMatch().containsKey("usage_metrics.idle_ratio")) {
                    basis.append("유휴 시간 비율 60% 이상\n");
                }
                basis.append("• 스케줄: ");
                if (params != null && params.containsKey("stop")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stopParams = (Map<String, Object>) params.get("stop");
                    if (stopParams != null) {
                        basis.append(stopParams.get("weekdays")).append(", ");
                        basis.append(stopParams.get("stop_at")).append(" ~ ").append(stopParams.get("start_at"));
                    }
                } else {
                    basis.append("주중 20:00 ~ 08:30");
                }
                basis.append("\n• 예상 절감액: ").append(String.format("%.0f원", savings));
                break;
                
            case "commitment":
                basis.append("장기 약정 최적화 규칙 적용:\n");
                basis.append("• 적용 조건: ");
                if (rule.getMatch().containsKey("usage_metrics.uptime_days")) {
                    basis.append("90일 이상 지속 실행 중인 인스턴스\n");
                }
                basis.append("• 약정 옵션: ");
                if (params != null && params.containsKey("commit_level")) {
                    basis.append((int)((Double) params.get("commit_level") * 100)).append("% 커버리지, ");
                }
                if (params != null && params.containsKey("commit_years")) {
                    basis.append(params.get("commit_years")).append("년 약정\n");
                } else {
                    basis.append("70% 커버리지, 1년 약정\n");
                }
                basis.append("• 예상 절감액: ").append(String.format("%.0f원", savings));
                break;
                
            case "storage":
                basis.append("스토리지 수명주기 최적화 규칙 적용:\n");
                basis.append("• 적용 조건: ");
                if (rule.getMatch().containsKey("usage_metrics.unused_days")) {
                    basis.append("30일 이상 미접근 스토리지\n");
                }
                basis.append("• 아카이빙 옵션: ");
                if (params != null && params.containsKey("target_tier")) {
                    basis.append(params.get("target_tier")).append(" 티어로 이동, ");
                }
                if (params != null && params.containsKey("retention_days")) {
                    basis.append(params.get("retention_days")).append("일 보존\n");
                } else {
                    basis.append("Cold 티어, 90일 보존\n");
                }
                basis.append("• 예상 절감액: ").append(String.format("%.0f원", savings));
                break;
                
            default:
                basis.append("최적화 규칙에 따라 비용 절감이 가능합니다.");
        }
        
        return basis.toString();
    }
    
    @lombok.Builder
    @lombok.Value
    public static class UcasRule {
        String ruleId;
        String actionType;
        String scope;
        Map<String, Object> match;
        Map<String, Object> params;
        String estimateFormula;
        Boolean approvalRequired;
    }
}

