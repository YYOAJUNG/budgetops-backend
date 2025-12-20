package com.budgetops.backend.costs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CostOptimizationRuleLoader 테스트")
class CostOptimizationRuleLoaderTest {

    @Test
    @DisplayName("규칙 로더 초기화")
    void loadRules_Initialization() {
        // when
        CostOptimizationRuleLoader loader = new CostOptimizationRuleLoader();

        // then
        assertThat(loader).isNotNull();
    }

    @Test
    @DisplayName("규칙 조회 - 존재하는 CSP와 서비스")
    void getRules_ExistingCspAndService() {
        // given
        CostOptimizationRuleLoader loader = new CostOptimizationRuleLoader();

        // when
        List<CostOptimizationRuleLoader.OptimizationRule> rules = loader.getRules("AWS", "EC2");

        // then
        // 실제 파일이 있으면 규칙이 있을 수 있음
        assertThat(rules).isNotNull();
    }

    @Test
    @DisplayName("규칙 조회 - 존재하지 않는 CSP와 서비스")
    void getRules_NonExistingCspAndService() {
        // given
        CostOptimizationRuleLoader loader = new CostOptimizationRuleLoader();

        // when
        List<CostOptimizationRuleLoader.OptimizationRule> rules = loader.getRules("UNKNOWN", "SERVICE");

        // then
        assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("모든 규칙 조회")
    void getAllRules_Success() {
        // given
        CostOptimizationRuleLoader loader = new CostOptimizationRuleLoader();

        // when
        List<CostOptimizationRuleLoader.OptimizationRule> allRules = loader.getAllRules();

        // then
        assertThat(allRules).isNotNull();
    }

    @Test
    @DisplayName("프롬프트용 규칙 포맷팅")
    void formatRulesForPrompt_Success() {
        // given
        CostOptimizationRuleLoader loader = new CostOptimizationRuleLoader();

        // when
        String formatted = loader.formatRulesForPrompt();

        // then
        assertThat(formatted).isNotNull();
        assertThat(formatted).contains("# 클라우드 비용 최적화 규칙");
    }
}

