package com.budgetops.backend.gcp.dto;

public class ServiceAccountKeyUploadRequest {
    private String serviceAccountKeyJson;

    public String getServiceAccountKeyJson() {
        return serviceAccountKeyJson;
    }

    public void setServiceAccountKeyJson(String serviceAccountKeyJson) {
        this.serviceAccountKeyJson = serviceAccountKeyJson;
    }
}


