package com.budgetops.backend.notification.service;

import com.budgetops.backend.billing.entity.Member;
import com.budgetops.backend.billing.repository.MemberRepository;
import com.budgetops.backend.notification.dto.SlackSettingsRequest;
import com.budgetops.backend.notification.dto.SlackSettingsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSettingsService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public SlackSettingsResponse getSlackSettings(String email) {
        Member member = findMemberByEmail(email);
        return new SlackSettingsResponse(
                Boolean.TRUE.equals(member.getSlackNotificationsEnabled()),
                member.getSlackWebhookUrl(),
                member.getUpdatedAt()
        );
    }

    @Transactional
    public SlackSettingsResponse updateSlackSettings(String email, SlackSettingsRequest request) {
        Member member = findMemberByEmail(email);

        if (Boolean.TRUE.equals(request.enabled()) && !StringUtils.hasText(request.webhookUrl())) {
            throw new IllegalArgumentException("Slack Webhook URL을 입력해주세요.");
        }

        member.setSlackNotificationsEnabled(request.enabled());
        member.setSlackWebhookUrl(StringUtils.hasText(request.webhookUrl())
                ? request.webhookUrl().trim()
                : null);

        Member saved = memberRepository.save(member);

        log.info("Updated slack settings for member {}", email);

        return new SlackSettingsResponse(
                Boolean.TRUE.equals(saved.getSlackNotificationsEnabled()),
                saved.getSlackWebhookUrl(),
                saved.getUpdatedAt()
        );
    }

    private Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + email));
    }
}

