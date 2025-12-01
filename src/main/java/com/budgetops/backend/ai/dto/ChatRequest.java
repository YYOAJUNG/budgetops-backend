package com.budgetops.backend.ai.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ChatRequest {
    @NotBlank(message = "메시지는 필수입니다.")
    private String message;
    
    private String sessionId;
}

