package com.budgetops.backend.azure.dto;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDate;

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
    LocalDate creditStartDate;
    LocalDate creditEndDate;
}

