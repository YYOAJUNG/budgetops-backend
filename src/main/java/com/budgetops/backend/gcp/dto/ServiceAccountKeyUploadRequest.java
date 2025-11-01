package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceAccountKeyUploadRequest {
    private String serviceAccountKeyJson;
}


