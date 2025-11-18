package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AzureCredentialValidator {

    private final AzureTokenManager tokenManager;
    private final AzureApiClient apiClient;

    /**
     * Azure AD Client Credential 흐름으로 토큰을 발급 받은 뒤
     * 구독 조회 API를 호출하여 자격 증명이 유효한지 확인한다.
     */
    public boolean isValid(String tenantId, String clientId, String clientSecret, String subscriptionId) {
        try {
            AzureAccessToken token = tokenManager.getToken(tenantId, clientId, clientSecret);
            apiClient.getSubscription(subscriptionId, token.getAccessToken());
            return true;
        } catch (Exception e) {
            log.warn("Azure credential validation failed for clientId={}, subscriptionId={}: {}", clientId, subscriptionId, e.getMessage());
            tokenManager.invalidate(tenantId, clientId, clientSecret);
            return false;
        }
    }
}

