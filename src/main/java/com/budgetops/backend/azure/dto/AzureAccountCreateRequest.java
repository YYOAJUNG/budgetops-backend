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

    /**
     * 이 계정이 Azure 크레딧(프리티어)을 사용 중인지 여부.
     * null인 경우 기본값(true)을 사용합니다.
     */
    private Boolean hasCredit;

    /**
     * 크레딧 한도 금액 (통화 단위는 비용 API에서 반환되는 통화 또는 계정 설정 통화 기준)
     * null인 경우 기본 한도(AzureFreeTierLimits.AZURE_SIGNUP_CREDIT_USD)를 사용합니다.
     */
    private Double creditLimitAmount;

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

    public Boolean getHasCredit() {
        return hasCredit;
    }

    public Double getCreditLimitAmount() {
        return creditLimitAmount;
    }
}

