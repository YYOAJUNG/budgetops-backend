package com.budgetops.backend.simulator.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * 시뮬레이션 요청 DTO
 */
@Value
@Builder
public class SimulateRequest {
    @NotEmpty(message = "리소스 ID 목록은 필수입니다")
    List<String> resourceIds;
    
    @NotNull(message = "액션 타입은 필수입니다")
    ActionType action;  // offhours, commitment, storage, rightsizing, cleanup
    
    ScenarioParams params;
}

