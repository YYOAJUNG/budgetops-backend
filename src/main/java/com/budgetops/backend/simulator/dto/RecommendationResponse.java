package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 추천 액션 응답 DTO
 */
@Value
@Builder
public class RecommendationResponse {
    String title;
    String description;
    Double estimatedSavings;
    String actionType;  // offhours, commitment, storage
    SimulationResult scenario;  // 상세 시나리오 정보
}

