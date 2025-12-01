package com.budgetops.backend.budget.entity;

import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "member_account_budget",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_account_budget_member_provider_account",
                        columnNames = {"member_id", "provider", "account_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberAccountBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 클라우드 제공자 식별자 (예: AWS, AZURE, GCP, NCP)
     */
    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    /**
     * 각 클라우드 계정의 내부 ID (AWS/Azure/GCP/NCP 계정 테이블의 PK)
     */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * 계정별 월 예산 한도 (KRW 기준)
     */
    @Column(name = "monthly_budget_limit", precision = 19, scale = 2, nullable = false)
    private BigDecimal monthlyBudgetLimit;

    /**
     * 계정별 예산 알림 임계값 (0~100)
     */
    @Column(name = "alert_threshold")
    private Integer alertThreshold;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}


