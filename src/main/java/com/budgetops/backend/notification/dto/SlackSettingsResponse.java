package com.budgetops.backend.notification.dto;

import java.time.LocalDateTime;

public record SlackSettingsResponse(
        boolean enabled,
        String webhookUrl,
        LocalDateTime updatedAt
) {
}
