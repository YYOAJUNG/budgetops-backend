package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * 시뮬레이션 응답 DTO
 */
@Value
@Builder
public class SimulateResponse {
    List<SimulationResult> scenarios;
    String actionType;
    Integer totalResources;
}

