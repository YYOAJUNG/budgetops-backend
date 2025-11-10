package com.budgetops.backend.billing.entity;

import com.budgetops.backend.billing.enums.BillingPlan;
import com.budgetops.backend.billing.enums.BillingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BillingPlan currentPlan = BillingPlan.FREE;

    @Column(nullable = false)
    @Builder.Default
    private int currentPrice = 0;

    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BillingStatus status = BillingStatus.ACTIVE;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(nullable = false)
    @Builder.Default
    private int currentTokens = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 요금제 변경
     */
    public void changePlan(BillingPlan newPlan) {
        this.currentPlan = newPlan;
        this.currentPrice = newPlan.getTotalPrice();
    }

    /**
     * 토큰 추가
     */
    public void addTokens(int tokens) {
        this.currentTokens += tokens;
    }

    /**
     * 무료 요금제인지 확인
     */
    public boolean isFreePlan() {
        return currentPlan == BillingPlan.FREE;
    }

    /**
     * 다음 청구일 설정 (현재 시간 + 1개월)
     */
    public void setNextBillingDateFromNow() {
        this.nextBillingDate = LocalDateTime.now().plusMonths(1);
    }

    /**
     * 구독 취소
     */
    public void cancelSubscription() {
        this.status = BillingStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    /**
     * 구독이 만료되었는지 확인 (취소 후 nextBillingDate가 지남)
     */
    public boolean isExpired() {
        return status == BillingStatus.CANCELED
                && nextBillingDate != null
                && LocalDateTime.now().isAfter(nextBillingDate);
    }

    /**
     * 구독이 활성 상태인지 확인
     */
    public boolean isActive() {
        return status == BillingStatus.ACTIVE;
    }

    /**
     * FREE 플랜으로 다운그레이드
     */
    public void downgradeToFree() {
        changePlan(BillingPlan.FREE);
    }
}
