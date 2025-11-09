package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class GcpResourceResponse {
    private String resourceName;
    private String resourceType;
    private BigDecimal monthlyCost;
    private String region;
    private String status;
    private Instant lastUpdated;
}

