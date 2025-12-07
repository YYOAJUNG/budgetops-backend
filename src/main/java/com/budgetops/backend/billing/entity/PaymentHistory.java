package com.budgetops.backend.billing.entity;

import com.budgetops.backend.billing.enums.PaymentStatus;
import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 결제 내역 엔티티
 * Member와 N:1 관계 (한 회원이 여러 결제 내역을 가질 수 있음)
 */
@Entity
@Table(name = "payment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "imp_uid", nullable = false)
    private String impUid;  // 포트원(Iamport) 결제 고유 번호

    @Column(name = "merchant_uid", nullable = false)
    private String merchantUid;  // 주문 번호

    @Column(name = "amount", nullable = false)
    private Integer amount;  // 결제 금액 (KRW)

    @Column(name = "payment_method")
    private String paymentMethod;  // 결제 수단 (card, kakaopay 등)

    @Column(name = "order_name")
    private String orderName;  // 주문명 (예: "Pro 플랜 결제", "토큰 10,000개 구매")

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;  // paid, pending, failed

    @Column(name = "paid_at")
    private LocalDateTime paidAt;  // 결제 완료 시각

    @Column(name = "failed_reason")
    private String failedReason;  // 결제 실패 사유

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 결제가 완료 상태인지 확인
     */
    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    /**
     * 결제 완료 처리
     */
    public void markAsPaid(LocalDateTime paidAt) {
        this.status = PaymentStatus.PAID;
        this.paidAt = paidAt;
    }

    /**
     * 결제 실패 처리
     */
    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failedReason = reason;
    }
}
