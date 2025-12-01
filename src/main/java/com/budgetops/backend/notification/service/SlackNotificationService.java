package com.budgetops.backend.notification.service;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class SlackNotificationService {

    private final RestTemplate restTemplate;

    public SlackNotificationService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void sendEc2Alert(String webhookUrl, AwsEc2Alert alert) {
        if (!StringUtils.hasText(webhookUrl) || alert == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("text", buildEc2AlertMessage(alert));

        try {
            restTemplate.postForEntity(webhookUrl, payload, String.class);
            log.debug("Sent Slack EC2 alert for instance {}", alert.getInstanceId());
        } catch (Exception ex) {
            log.warn("Failed to send Slack alert: {}", ex.getMessage());
        }
    }

    private String buildEc2AlertMessage(AwsEc2Alert alert) {
        String severityEmoji = switch (Optional.ofNullable(alert.getSeverity()).orElse(AwsEc2Alert.AlertSeverity.INFO)) {
            case CRITICAL -> ":rotating_light:";
            case WARNING -> ":warning:";
            default -> ":information_source:";
        };

        return """
                %s *AWS EC2 알림*
                • 계정: %s
                • 인스턴스: %s (%s)
                • 규칙: %s
                • 메트릭: %s
                • 현재값: %.2f (임계값 %.2f)
                """.formatted(
                severityEmoji,
                Optional.ofNullable(alert.getAccountName()).orElse("미지정"),
                Optional.ofNullable(alert.getInstanceName()).orElse(alert.getInstanceId()),
                alert.getInstanceId(),
                Optional.ofNullable(alert.getRuleTitle()).orElse("알 수 없음"),
                Optional.ofNullable(alert.getViolatedMetric()).orElse("알 수 없음"),
                Optional.ofNullable(alert.getCurrentValue()).orElse(0.0),
                Optional.ofNullable(alert.getThreshold()).orElse(0.0)
        );
    }
}

