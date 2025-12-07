package com.budgetops.backend.azure.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AzureAccountResponse {
    Long id;
    String name;
    String subscriptionId;
    String tenantId;
    String clientId;
    String clientSecretLast4;
    boolean active;
    Boolean hasCredit;
    Double creditLimitAmount;
}

