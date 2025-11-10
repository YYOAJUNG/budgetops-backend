package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GcpResourceListResponse {
    private Long accountId;
    private String projectId;
    private List<GcpResourceResponse> resources;
}

