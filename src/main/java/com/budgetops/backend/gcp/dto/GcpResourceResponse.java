package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class GcpResourceResponse {
    private Long id; // 우리 서비스 내부에서 부여하는 고유 ID
    private String resourceId; // GCP API의 additionalAttributes.id 값
    private String resourceName;
    private String resourceType;
    private String resourceTypeShort;
    private BigDecimal monthlyCost;
    private String region;
    private String status;
    private Instant lastUpdated;
}

