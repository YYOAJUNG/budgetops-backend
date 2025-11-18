package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveIntegrationResponse {
    private boolean ok;
    private Long id;
    private String name; // 사용자가 입력한 계정 이름
    private String serviceAccountId;
    private String projectId;
    private String message;

    public boolean isOk() {
        return ok;
    }
}


