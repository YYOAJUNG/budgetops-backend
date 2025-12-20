package com.budgetops.backend.aws.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwsEc2RuleLoader 테스트")
class AwsEc2RuleLoaderTest {

    @Test
    @DisplayName("RuleLoader 초기화 및 규칙 로드")
    void ruleLoader_Initialization() {
        // given & when
        AwsEc2RuleLoader ruleLoader = new AwsEc2RuleLoader();

        // then
        assertThat(ruleLoader).isNotNull();
        assertThat(ruleLoader.getAllRules()).isNotNull();
    }

    @Test
    @DisplayName("모든 규칙 조회")
    void getAllRules_Success() {
        // given
        AwsEc2RuleLoader ruleLoader = new AwsEc2RuleLoader();

        // when
        var rules = ruleLoader.getAllRules();

        // then
        assertThat(rules).isNotNull();
    }

    @Test
    @DisplayName("특정 규칙 ID로 조회")
    void getRuleById_Success() {
        // given
        AwsEc2RuleLoader ruleLoader = new AwsEc2RuleLoader();

        // when
        var rule = ruleLoader.getRuleById("non-existent-rule");

        // then
        // 규칙이 없으면 null 반환
        assertThat(rule).isNull();
    }
}

