package com.budgetops.backend.notification.service;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlackNotification Service 테스트")
class SlackNotificationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    private SlackNotificationService slackNotificationService;

    private final String testWebhookUrl = "https://hooks.slack.com/services/TEST/WEBHOOK/URL";

    @BeforeEach
    void setUp() {
        // RestTemplateBuilder가 RestTemplate을 반환하도록 설정
        when(restTemplateBuilder.setConnectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        slackNotificationService = new SlackNotificationService(restTemplateBuilder);
    }

    @Test
    @DisplayName("sendEc2Alert - EC2 알림 전송 성공")
    void sendEc2Alert_Success() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .accountName("Test Account")
                .instanceId("i-1234567890abcdef0")
                .instanceName("Test Instance")
                .ruleId("cpu_low")
                .ruleTitle("CPU 사용률 낮음")
                .violatedMetric("cpu_utilization")
                .currentValue(5.0)
                .threshold(10.0)
                .message("CPU 사용률이 낮습니다")
                .severity(AwsEc2Alert.AlertSeverity.WARNING)
                .status(AwsEc2Alert.AlertStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        given(restTemplate.postForEntity(eq(testWebhookUrl), any(Map.class), eq(String.class)))
                .willReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        // when
        slackNotificationService.sendEc2Alert(testWebhookUrl, alert);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(testWebhookUrl), payloadCaptor.capture(), eq(String.class));

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsKey("text");
        String message = (String) payload.get("text");
        assertThat(message).contains("AWS EC2 알림");
        assertThat(message).contains("Test Account");
        assertThat(message).contains("Test Instance");
        assertThat(message).contains("i-1234567890abcdef0");
        assertThat(message).contains("CPU 사용률 낮음");
    }

    @Test
    @DisplayName("sendEc2Alert - CRITICAL 심각도 알림")
    void sendEc2Alert_CriticalSeverity() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .accountName("Test Account")
                .instanceId("i-1234567890abcdef0")
                .instanceName("Test Instance")
                .ruleId("cpu_critical")
                .ruleTitle("심각한 CPU 낭비")
                .violatedMetric("cpu_utilization")
                .currentValue(2.0)
                .threshold(10.0)
                .severity(AwsEc2Alert.AlertSeverity.CRITICAL)
                .status(AwsEc2Alert.AlertStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        given(restTemplate.postForEntity(anyString(), any(Map.class), eq(String.class)))
                .willReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        // when
        slackNotificationService.sendEc2Alert(testWebhookUrl, alert);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));

        Map<String, Object> payload = payloadCaptor.getValue();
        String message = (String) payload.get("text");
        assertThat(message).contains(":rotating_light:");
    }

    @Test
    @DisplayName("sendEc2Alert - WARNING 심각도 알림")
    void sendEc2Alert_WarningSeverity() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .instanceId("i-1234567890abcdef0")
                .severity(AwsEc2Alert.AlertSeverity.WARNING)
                .createdAt(LocalDateTime.now())
                .build();

        given(restTemplate.postForEntity(anyString(), any(Map.class), eq(String.class)))
                .willReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        // when
        slackNotificationService.sendEc2Alert(testWebhookUrl, alert);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));

        Map<String, Object> payload = payloadCaptor.getValue();
        String message = (String) payload.get("text");
        assertThat(message).contains(":warning:");
    }

    @Test
    @DisplayName("sendEc2Alert - INFO 심각도 알림")
    void sendEc2Alert_InfoSeverity() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .instanceId("i-1234567890abcdef0")
                .severity(AwsEc2Alert.AlertSeverity.INFO)
                .createdAt(LocalDateTime.now())
                .build();

        given(restTemplate.postForEntity(anyString(), any(Map.class), eq(String.class)))
                .willReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        // when
        slackNotificationService.sendEc2Alert(testWebhookUrl, alert);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));

        Map<String, Object> payload = payloadCaptor.getValue();
        String message = (String) payload.get("text");
        assertThat(message).contains(":information_source:");
    }

    @Test
    @DisplayName("sendEc2Alert - Webhook URL이 null인 경우 아무 작업도 하지 않음")
    void sendEc2Alert_NullWebhookUrl() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .instanceId("i-1234567890abcdef0")
                .build();

        // when
        slackNotificationService.sendEc2Alert(null, alert);

        // then
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("sendEc2Alert - Webhook URL이 빈 문자열인 경우 아무 작업도 하지 않음")
    void sendEc2Alert_EmptyWebhookUrl() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .instanceId("i-1234567890abcdef0")
                .build();

        // when
        slackNotificationService.sendEc2Alert("", alert);

        // then
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("sendEc2Alert - alert가 null인 경우 아무 작업도 하지 않음")
    void sendEc2Alert_NullAlert() {
        // when
        slackNotificationService.sendEc2Alert(testWebhookUrl, null);

        // then
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    @DisplayName("sendEc2Alert - 전송 실패 시 예외를 던지지 않고 로그만 남김")
    void sendEc2Alert_SendFailure() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .instanceId("i-1234567890abcdef0")
                .createdAt(LocalDateTime.now())
                .build();

        given(restTemplate.postForEntity(anyString(), any(Map.class), eq(String.class)))
                .willThrow(new RestClientException("Network error"));

        // when & then
        assertThatCode(() -> slackNotificationService.sendEc2Alert(testWebhookUrl, alert))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendTestMessage - 테스트 메시지 전송 성공")
    void sendTestMessage_Success() {
        // given
        given(restTemplate.postForEntity(eq(testWebhookUrl), any(Map.class), eq(String.class)))
                .willReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        // when
        slackNotificationService.sendTestMessage(testWebhookUrl);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(eq(testWebhookUrl), payloadCaptor.capture(), eq(String.class));

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsKey("text");
        String message = (String) payload.get("text");
        assertThat(message).contains("Slack 알림 테스트");
        assertThat(message).contains("BudgetOps");
        assertThat(message).contains("정상적으로 작동");
    }

    @Test
    @DisplayName("sendTestMessage - Webhook URL이 null인 경우 예외 발생")
    void sendTestMessage_NullWebhookUrl() {
        // when & then
        assertThatThrownBy(() -> slackNotificationService.sendTestMessage(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook URL이 필요합니다.");
    }

    @Test
    @DisplayName("sendTestMessage - Webhook URL이 빈 문자열인 경우 예외 발생")
    void sendTestMessage_EmptyWebhookUrl() {
        // when & then
        assertThatThrownBy(() -> slackNotificationService.sendTestMessage("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Webhook URL이 필요합니다.");
    }

    @Test
    @DisplayName("sendTestMessage - 전송 실패 시 RuntimeException 발생")
    void sendTestMessage_SendFailure() {
        // given
        given(restTemplate.postForEntity(anyString(), any(Map.class), eq(String.class)))
                .willThrow(new RestClientException("Network error"));

        // when & then
        assertThatThrownBy(() -> slackNotificationService.sendTestMessage(testWebhookUrl))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Slack 메시지 전송에 실패했습니다")
                .hasMessageContaining("Network error");
    }

    @Test
    @DisplayName("sendEc2Alert - Optional 필드가 null인 경우 기본값 사용")
    void sendEc2Alert_OptionalFieldsNull() {
        // given
        AwsEc2Alert alert = AwsEc2Alert.builder()
                .accountId(1L)
                .instanceId("i-1234567890abcdef0")
                .createdAt(LocalDateTime.now())
                .build();

        given(restTemplate.postForEntity(anyString(), any(Map.class), eq(String.class)))
                .willReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        // when
        slackNotificationService.sendEc2Alert(testWebhookUrl, alert);

        // then
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restTemplate).postForEntity(anyString(), payloadCaptor.capture(), eq(String.class));

        Map<String, Object> payload = payloadCaptor.getValue();
        String message = (String) payload.get("text");
        assertThat(message).contains("미지정");  // accountName이 null일 때
        assertThat(message).contains("알 수 없음");  // ruleTitle, violatedMetric이 null일 때
        assertThat(message).contains("0.00");  // currentValue, threshold가 null일 때
    }
}

