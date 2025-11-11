package com.budgetops.backend.simulator.dto;

/**
 * 액션 타입 enum
 */
public enum ActionType {
    OFFHOURS("offhours", "Off-hours 스케줄링"),
    COMMITMENT("commitment", "Commitment 최적화"),
    STORAGE("storage", "Storage 수명주기"),
    RIGHTSIZING("rightsizing", "Rightsizing"),
    CLEANUP("cleanup", "Zombie 청소");
    
    private final String code;
    private final String description;
    
    ActionType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
}

