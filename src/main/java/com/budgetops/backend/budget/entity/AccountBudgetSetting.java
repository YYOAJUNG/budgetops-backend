package com.budgetops.backend.budget.entity;

import com.budgetops.backend.budget.model.CloudProvider;
import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_budget_setting", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_budget_setting_member_provider_account",
                columnNames = {"member_id", "provider", "provider_account_id"})
})
public class AccountBudgetSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CloudProvider provider;

    @Column(name = "provider_account_id", nullable = false)
    private Long providerAccountId;

    @Column(name = "account_name_snapshot")
    private String accountNameSnapshot;

    @Column(name = "monthly_budget_limit", precision = 19, scale = 2, nullable = false)
    private BigDecimal monthlyBudgetLimit;

    @Column(name = "alert_threshold")
    private Integer alertThreshold;

    @Column(name = "alert_triggered_at")
    private LocalDateTime alertTriggeredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

