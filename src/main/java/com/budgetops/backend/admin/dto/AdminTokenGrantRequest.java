package com.budgetops.backend.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminTokenGrantRequest {
    @NotNull(message = "토큰 수량은 필수입니다.")
    @Min(value = 1, message = "토큰 수량은 1 이상이어야 합니다.")
    private Integer tokens;
    
    private String reason; // 관리자가 토큰을 부여한 이유 (선택사항)
}

