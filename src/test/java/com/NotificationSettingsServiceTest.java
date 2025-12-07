package com.budgetops.backend.notification.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.notification.dto.SlackSettingsRequest;
import com.budgetops.backend.notification.dto.SlackSettingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSettings Service 테스트")
class NotificationSettingsServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private NotificationSettingsService notificationSettingsService;

    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");
        testMember.setSlackWebhookUrl("https://hooks.slack.com/services/TEST/WEBHOOK/URL");
        testMember.setSlackNotificationsEnabled(true);
        testMember.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("getSlackSettings - Slack 설정 조회 성공")
    void getSlackSettings_Success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        // when
        SlackSettingsResponse response = notificationSettingsService.getSlackSettings(1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.enabled()).isTrue();
        assertThat(response.webhookUrl()).startsWith("https://");
        assertThat(response.webhookUrl()).contains("********");
        assertThat(response.updatedAt()).isNotNull();
        verify(memberRepository).findById(1L);
    }

    @Test
    @DisplayName("getSlackSettings - Webhook URL 마스킹 확인")
    void getSlackSettings_WebhookUrlMasked() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        // when
        SlackSettingsResponse response = notificationSettingsService.getSlackSettings(1L);

        // then
        assertThat(response.webhookUrl()).isEqualTo("https://********");
    }

    @Test
    @DisplayName("getSlackSettings - Webhook URL이 짧은 경우")
    void getSlackSettings_ShortWebhookUrl() {
        // given
        testMember.setSlackWebhookUrl("short");
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        // when
        SlackSettingsResponse response = notificationSettingsService.getSlackSettings(1L);

        // then
        assertThat(response.webhookUrl()).isNull();
    }

    @Test
    @DisplayName("getSlackSettings - Webhook URL이 null인 경우")
    void getSlackSettings_NullWebhookUrl() {
        // given
        testMember.setSlackWebhookUrl(null);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        // when
        SlackSettingsResponse response = notificationSettingsService.getSlackSettings(1L);

        // then
        assertThat(response.webhookUrl()).isNull();
    }

    @Test
    @DisplayName("getSlackSettings - 존재하지 않는 회원")
    void getSlackSettings_MemberNotFound() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationSettingsService.getSlackSettings(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Member not found: 1");
    }

    @Test
    @DisplayName("updateSlackSettings - Slack 설정 업데이트 성공")
    void updateSlackSettings_Success() {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest(
                "https://hooks.slack.com/services/T1234567890/B1234567890/abcdefghijklmnop",
                true
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);

        // when
        SlackSettingsResponse response = notificationSettingsService.updateSlackSettings(1L, request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.enabled()).isTrue();

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getSlackNotificationsEnabled()).isTrue();
        assertThat(savedMember.getSlackWebhookUrl()).isEqualTo("https://hooks.slack.com/services/T1234567890/B1234567890/abcdefghijklmnop");
    }

    @Test
    @DisplayName("updateSlackSettings - Slack 알림 비활성화")
    void updateSlackSettings_Disable() {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest(null, false);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);

        // when
        SlackSettingsResponse response = notificationSettingsService.updateSlackSettings(1L, request);

        // then
        assertThat(response).isNotNull();

        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getSlackNotificationsEnabled()).isFalse();
        assertThat(savedMember.getSlackWebhookUrl()).isNull();
    }

    @Test
    @DisplayName("updateSlackSettings - Webhook URL 공백 제거")
    void updateSlackSettings_TrimWebhookUrl() {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest(
                "  https://hooks.slack.com/services/T1234567890/B1234567890/abcdefghijklmnop  ",
                true
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);

        // when
        notificationSettingsService.updateSlackSettings(1L, request);

        // then
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getSlackWebhookUrl()).isEqualTo("https://hooks.slack.com/services/T1234567890/B1234567890/abcdefghijklmnop");
    }

    @Test
    @DisplayName("updateSlackSettings - 활성화하려는데 Webhook URL이 없는 경우 예외 발생")
    void updateSlackSettings_EnabledWithoutWebhookUrl() {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest(null, true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        // when & then
        assertThatThrownBy(() -> notificationSettingsService.updateSlackSettings(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack Webhook URL을 입력해주세요.");
    }

    @Test
    @DisplayName("updateSlackSettings - 활성화하려는데 Webhook URL이 빈 문자열인 경우 예외 발생")
    void updateSlackSettings_EnabledWithEmptyWebhookUrl() {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest("   ", true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

        // when & then
        assertThatThrownBy(() -> notificationSettingsService.updateSlackSettings(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slack Webhook URL을 입력해주세요.");
    }

    @Test
    @DisplayName("updateSlackSettings - 존재하지 않는 회원")
    void updateSlackSettings_MemberNotFound() {
        // given
        SlackSettingsRequest request = new SlackSettingsRequest("https://hooks.slack.com/test", true);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationSettingsService.updateSlackSettings(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Member not found: 1");
    }

    @Test
    @DisplayName("updateSlackSettings - 기존 설정 덮어쓰기")
    void updateSlackSettings_Overwrite() {
        // given
        testMember.setSlackNotificationsEnabled(false);
        testMember.setSlackWebhookUrl(null);

        SlackSettingsRequest request = new SlackSettingsRequest(
                "https://hooks.slack.com/services/T1234567890/B1234567890/abcdefghijklmnop",
                true
        );
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);

        // when
        notificationSettingsService.updateSlackSettings(1L, request);

        // then
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getSlackNotificationsEnabled()).isTrue();
        assertThat(savedMember.getSlackWebhookUrl()).isEqualTo("https://hooks.slack.com/services/T1234567890/B1234567890/abcdefghijklmnop");
    }
}

