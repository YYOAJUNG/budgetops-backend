package com.budgetops.backend.ai.service;

import com.budgetops.backend.costs.CostOptimizationRuleLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleResourceMatcher 테스트")
class RuleResourceMatcherTest {

    @Mock
    private CostOptimizationRuleLoader ruleLoader;

    @InjectMocks
    private RuleResourceMatcher ruleResourceMatcher;

    @Test
    @DisplayName("규칙 매칭 - null 분석 결과")
    void matchRules_NullAnalysis() {
        // when
        List<RuleResourceMatcher.MatchedRule> result = 
                ruleResourceMatcher.matchRules(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("규칙 매칭 - 빈 분석 결과")
    void matchRules_EmptyAnalysis() {
        // given
        given(ruleLoader.getRules(anyString(), anyString()))
                .willReturn(Collections.emptyList());
        
        ResourceAnalysisService.ResourceAnalysisResult analysis = 
                ResourceAnalysisService.ResourceAnalysisResult.builder()
                        .awsResources(Collections.emptyMap())
                        .azureResources(Collections.emptyMap())
                        .gcpResources(Collections.emptyMap())
                        .ncpResources(Collections.emptyMap())
                        .build();

        // when
        List<RuleResourceMatcher.MatchedRule> result = 
                ruleResourceMatcher.matchRules(analysis);

        // then
        assertThat(result).isEmpty();
    }
}

