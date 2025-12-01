package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillingAccountTestRequest {
    private String billingAccountId;
    private String serviceAccountKeyJson;
}

