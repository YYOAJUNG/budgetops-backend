package com.budgetops.backend.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SlackSettingsRequest(
        @NotNull(message = "Slack 알림 사용 여부를 선택해주세요.")
        Boolean enabled,

        @Size(max = 1024, message = "Webhook URL은 1024자 이하로 입력해주세요.")
        String webhookUrl
) {
}

