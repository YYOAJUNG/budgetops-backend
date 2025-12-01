package com.budgetops.backend.notification.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record SlackSettingsRequest(
        @Pattern(regexp = "^https://hooks.slack.com/services/T[a-zA-Z0-9_]+/B[a-zA-Z0-9_]+/[a-zA-Z0-9_]+$",
                message = "유효한 Slack Webhook URL 형식이 아닙니다.")
        String webhookUrl,
        Boolean enabled
) {
}

