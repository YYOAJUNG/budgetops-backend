package com.budgetops.backend.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @ManyToMany(mappedBy = "members")
    @Builder.Default
    private List<Workspace> workspaces = new ArrayList<>();

    @Column(name = "slack_webhook_url", length = 1024)
    private String slackWebhookUrl;

    @Column(name = "slack_notifications_enabled")
    @Builder.Default
    private Boolean slackNotificationsEnabled = Boolean.FALSE;

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
}
