package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ServiceAccountKeyUploadRequest {
    private String serviceAccountKeyJson;
}

