package com.budgetops.backend.notification.controller;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.notification.dto.SlackSettingsRequest;
import com.budgetops.backend.notification.dto.SlackSettingsResponse;
import com.budgetops.backend.notification.service.NotificationSettingsService;
import com.budgetops.backend.notification.service.SlackNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;
    private final SlackNotificationService slackNotificationService;
    private final MemberRepository memberRepository;

    @GetMapping("/slack")
    public ResponseEntity<SlackSettingsResponse> getSlackSettings() {
        Long memberId = getCurrentMemberId();
        return ResponseEntity.ok(notificationSettingsService.getSlackSettings(memberId));
    }

    @PutMapping("/slack")
    public ResponseEntity<SlackSettingsResponse> updateSlackSettings(
            @Valid @RequestBody SlackSettingsRequest request
    ) {
        Long memberId = getCurrentMemberId();
        return ResponseEntity.ok(notificationSettingsService.updateSlackSettings(memberId, request));
    }

    @PostMapping("/slack/test")
    public ResponseEntity<Map<String, String>> testSlackNotification() {
        Long memberId = getCurrentMemberId();
        SlackSettingsResponse settings = notificationSettingsService.getSlackSettings(memberId);
        
        if (!settings.enabled() || settings.webhookUrl() == null || settings.webhookUrl().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Slack 알림이 활성화되지 않았거나 Webhook URL이 설정되지 않았습니다."));
        }

        try {
            // 마스킹된 URL이므로 실제 URL을 다시 가져와야 함
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
            
            slackNotificationService.sendTestMessage(member.getSlackWebhookUrl());
            return ResponseEntity.ok(Map.of("message", "테스트 메시지가 성공적으로 전송되었습니다."));
        } catch (Exception e) {
            log.error("Slack 테스트 메시지 전송 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "테스트 메시지 전송에 실패했습니다: " + e.getMessage()));
        }
    }

    private Long getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("인증되지 않은 사용자입니다.");
        }
        return (Long) authentication.getPrincipal();
    }
}
