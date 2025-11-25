package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 시뮬레이션 결과 모델
 */
@Value
@Builder
public class SimulationResult {
    String scenarioName;
    Double newCost;  // 변경 후 비용
    Double currentCost;  // 현재 비용
    Double savings;  // 절감액
    Double riskScore;  // 리스크 스코어 (0~1)
    Double priorityScore;  // 우선순위 점수
    Double confidence;  // 확신도 (1 - riskScore)
    String yamlPatch;  // 적용 가능한 YAML 패치 (선택)
    String description;  // 시나리오 설명
}

