package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveIntegrationRequest {
    private String serviceAccountId;
    private String serviceAccountKeyJson;
    private String billingAccountId;
}

