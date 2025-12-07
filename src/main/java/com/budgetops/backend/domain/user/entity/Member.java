package com.budgetops.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "monthly_budget_limit", precision = 19, scale = 2)
    private BigDecimal monthlyBudgetLimit;

    @Column(name = "budget_alert_threshold")
    private Integer budgetAlertThreshold;

    @Column(name = "budget_alert_triggered_at")
    private LocalDateTime budgetAlertTriggeredAt;

    @Column(name = "slack_webhook_url", length = 2048)
    private String slackWebhookUrl;

    @Column(name = "slack_notifications_enabled")
    @Builder.Default
    private Boolean slackNotificationsEnabled = Boolean.FALSE;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt; // 마지막 로그인 시각
}
