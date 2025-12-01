package com.budgetops.backend.notification.controller;

import com.budgetops.backend.notification.dto.SlackSettingsRequest;
import com.budgetops.backend.notification.dto.SlackSettingsResponse;
import com.budgetops.backend.notification.service.NotificationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping("/slack")
    public ResponseEntity<SlackSettingsResponse> getSlackSettings() {
        String email = getCurrentUserEmail();
        return ResponseEntity.ok(notificationSettingsService.getSlackSettings(email));
    }

    @PutMapping("/slack")
    public ResponseEntity<SlackSettingsResponse> updateSlackSettings(
            @Valid @RequestBody SlackSettingsRequest request
    ) {
        String email = getCurrentUserEmail();
        return ResponseEntity.ok(notificationSettingsService.updateSlackSettings(email, request));
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("인증되지 않은 사용자입니다.");
        }
        return authentication.getName();
    }
}

