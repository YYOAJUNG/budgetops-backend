package com.budgetops.backend.azure.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AzureCredentialValidator {

    /**
     * 실제 구현 시 Azure AD 토큰 발급 및 ARM 간단 호출로 자격 증명을 검증한다.
     * 현재는 스켈레톤 단계라 항상 true를 반환한다.
     */
    public boolean isValid(String tenantId, String clientId, String clientSecret, String subscriptionId) {
        log.debug("Skipping Azure credential validation (stub) for clientId={}, subscriptionId={}", clientId, subscriptionId);
        return true;
    }
}

