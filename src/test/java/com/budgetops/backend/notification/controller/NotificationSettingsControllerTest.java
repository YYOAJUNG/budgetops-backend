package com.budgetops.backend.notification.controller;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.notification.dto.SlackSettingsRequest;
import com.budgetops.backend.notification.dto.SlackSettingsResponse;
import com.budgetops.backend.notification.service.NotificationSettingsService;
import com.budgetops.backend.notification.service.SlackNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSettings Controller 테스트")
class NotificationSettingsControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NotificationSettingsService notificationSettingsService;

    @Mock
    private SlackNotificationService slackNotificationService;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private NotificationSettingsController notificationSettingsController;

    private Member testMember;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationSettingsController).build();
        
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");
        testMember.setSlackWebhookUrl("https://hooks.slack.com/services/TEST/WEBHOOK/URL");
        testMember.setSlackNotificationsEnabled(true);

        // memberId를 principal로 사용하는 Authentication 객체 생성
        authentication = new UsernamePasswordAuthenticationToken(
                1L, // principal은 memberId
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        
        // SecurityContext에 authentication 설정
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("GET /api/notifications/slack - Slack 설정 조회 성공")
    void getSlackSettings_Success() throws Exception {
        // given
        SlackSettingsResponse response = new SlackSettingsResponse(
                true,
                "https://********",
                LocalDateTime.now()
        );
        given(notificationSettingsService.getSlackSettings(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/notifications/slack")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.webhookUrl").value("https://********"))
                .andExpect(jsonPath("$.updatedAt").exists());

        verify(notificationSettingsService).getSlackSettings(1L);
    }

    @Test
    @DisplayName("PUT /api/notifications/slack - Slack 설정 업데이트 성공")
    void updateSlackSettings_Success() throws Exception {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest(
                "https://hooks.slack.com/services/T1234567890/B1234567890/abcdefghijklmnop",
                true
        );
        SlackSettingsResponse response = new SlackSettingsResponse(
                true,
                "https://********",
                LocalDateTime.now()
        );
        given(notificationSettingsService.updateSlackSettings(eq(1L), any(SlackSettingsRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(put("/api/notifications/slack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.webhookUrl").value("https://********"));

        verify(notificationSettingsService).updateSlackSettings(eq(1L), any(SlackSettingsRequest.class));
    }

    @Test
    @DisplayName("PUT /api/notifications/slack - Slack 알림 비활성화")
    void updateSlackSettings_Disable() throws Exception {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest(null, false);
        SlackSettingsResponse response = new SlackSettingsResponse(
                false,
                null,
                LocalDateTime.now()
        );
        given(notificationSettingsService.updateSlackSettings(eq(1L), any(SlackSettingsRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(put("/api/notifications/slack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.webhookUrl").isEmpty());

        verify(notificationSettingsService).updateSlackSettings(eq(1L), any(SlackSettingsRequest.class));
    }

    @Test
    @DisplayName("POST /api/notifications/slack/test - 테스트 메시지 전송 성공")
    void testSlackNotification_Success() throws Exception {
        // given
        SlackSettingsResponse settings = new SlackSettingsResponse(
                true,
                "https://********",
                LocalDateTime.now()
        );
        given(notificationSettingsService.getSlackSettings(1L)).willReturn(settings);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        // when & then
        mockMvc.perform(post("/api/notifications/slack/test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("테스트 메시지가 성공적으로 전송되었습니다."));

        verify(slackNotificationService).sendTestMessage(testMember.getSlackWebhookUrl());
    }

    @Test
    @DisplayName("POST /api/notifications/slack/test - Slack 알림이 비활성화된 경우")
    void testSlackNotification_NotEnabled() throws Exception {
        // given
        SlackSettingsResponse settings = new SlackSettingsResponse(
                false,
                null,
                LocalDateTime.now()
        );
        given(notificationSettingsService.getSlackSettings(1L)).willReturn(settings);

        // when & then
        mockMvc.perform(post("/api/notifications/slack/test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Slack 알림이 활성화되지 않았거나 Webhook URL이 설정되지 않았습니다."));
    }

    @Test
    @DisplayName("POST /api/notifications/slack/test - Webhook URL이 없는 경우")
    void testSlackNotification_NoWebhookUrl() throws Exception {
        // given
        SlackSettingsResponse settings = new SlackSettingsResponse(
                true,
                null,
                LocalDateTime.now()
        );
        given(notificationSettingsService.getSlackSettings(1L)).willReturn(settings);

        // when & then
        mockMvc.perform(post("/api/notifications/slack/test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Slack 알림이 활성화되지 않았거나 Webhook URL이 설정되지 않았습니다."));
    }

    @Test
    @DisplayName("POST /api/notifications/slack/test - 전송 실패")
    void testSlackNotification_SendFailed() throws Exception {
        // given
        SlackSettingsResponse settings = new SlackSettingsResponse(
                true,
                "https://********",
                LocalDateTime.now()
        );
        given(notificationSettingsService.getSlackSettings(1L)).willReturn(settings);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        doThrow(new RuntimeException("네트워크 오류"))
                .when(slackNotificationService).sendTestMessage(anyString());

        // when & then
        mockMvc.perform(post("/api/notifications/slack/test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("테스트 메시지 전송에 실패했습니다: 네트워크 오류"));
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 요청 - IllegalStateException 발생")
    void unauthenticated_Request() {
        // given
        SecurityContextHolder.clearContext();
        
        // when & then
        try {
            notificationSettingsController.getSlackSettings();
            // 예외가 발생하지 않으면 실패
            throw new AssertionError("IllegalStateException이 발생해야 합니다");
        } catch (IllegalStateException e) {
            // 예상된 동작
            assertThat(e.getMessage()).contains("인증되지 않은 사용자입니다");
        }
    }
}

