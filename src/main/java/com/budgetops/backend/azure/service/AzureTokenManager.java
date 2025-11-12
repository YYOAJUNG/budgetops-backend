package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AzureTokenManager {

    private final AzureApiClient apiClient;
    private final Map<String, AzureAccessToken> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public AzureAccessToken getToken(String tenantId, String clientId, String clientSecret) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(clientSecret, "clientSecret must not be null");

        String key = cacheKey(tenantId, clientId, clientSecret);
        AzureAccessToken cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            cached = cache.get(key);
            if (cached != null && !cached.isExpired()) {
                return cached;
            }
            AzureAccessToken fresh = apiClient.fetchToken(tenantId, clientId, clientSecret);
            cache.put(key, fresh);
            return fresh;
        }
    }

    public void invalidate(String tenantId, String clientId, String clientSecret) {
        String key = cacheKey(tenantId, clientId, clientSecret);
        cache.remove(key);
    }

    private String cacheKey(String tenantId, String clientId, String clientSecret) {
        return tenantId + "|" + clientId + "|" + clientSecret.hashCode();
    }
}

