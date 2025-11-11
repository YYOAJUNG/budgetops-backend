package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Map;

/**
 * 시나리오 파라미터 모델
 */
@Value
@Builder
public class ScenarioParams {
    // Rightsizing
    String targetSize;  // 인스턴스 타입/스펙
    Integer targetVcpu;
    Integer targetRam;
    Integer targetIops;
    
    // Commitment
    Double commitLevel;  // 0.5, 0.7, 0.9 (50%, 70%, 90%)
    Integer commitYears;  // 1, 3
    
    // Off-hours
    List<String> weekdays;  // ["Mon-Fri"]
    String stopAt;  // "20:00"
    String startAt;  // "08:30"
    String timezone;  // "Asia/Seoul"
    Boolean scaleToZeroSupported;
    
    // Storage lifecycle
    String targetTier;  // "Cold", "Archive"
    Integer retentionDays;  // 스냅샷 보존 기간
    
    // Cleanup
    Integer unusedDays;  // 미사용 일수
    
    // 기타
    Map<String, Object> customParams;
}

