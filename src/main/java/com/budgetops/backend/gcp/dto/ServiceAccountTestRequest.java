package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceAccountTestRequest {
    private String serviceAccountId;
    private String serviceAccountKeyJson;
}

