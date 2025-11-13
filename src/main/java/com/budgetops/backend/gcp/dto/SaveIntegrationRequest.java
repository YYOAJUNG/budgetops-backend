package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveIntegrationRequest {
    private String name; // 사용자가 입력한 계정 이름
    private String serviceAccountId;
    private String serviceAccountKeyJson;
    private String billingAccountId;
}

