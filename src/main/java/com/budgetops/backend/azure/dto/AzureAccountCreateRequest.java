package com.budgetops.backend.azure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AzureAccountCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String subscriptionId;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    public String getName() {
        return name;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }
}

