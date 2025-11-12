package com.budgetops.backend.azure.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class AzureAccessToken {
    String tokenType;
    String accessToken;
    OffsetDateTime expiresAt;

    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt.minusMinutes(1));
    }
}

