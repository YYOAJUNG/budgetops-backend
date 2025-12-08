package com.budgetops.backend.gcp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * GCP 크레딧 엔티티
 * 
 * 크레딧은 추가 이력으로 관리되며, 각 추가마다 만료일 존재
 * 계정당 여러 개의 크레딧 레코드를 가질 수 있음
 * 
 * 크레딧 사용 규칙:
 * - 크레딧 추가 시: 새로운 레코드가 생성되며 creditAmount가 설정됨
 * - 크레딧 사용 시: 만료일이 빠른 순서부터 creditAmount가 감소됨
 * - 만료된 크레딧: 남은 creditAmount는 0으로 설정됨
 */
@Entity
@Table(name = "gcp_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class GcpCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * GCP 계정 (ManyToOne)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gcp_account_id", nullable = false)
    private GcpAccount gcpAccount;

    /**
     * 크레딧량
     * 
     * 크레딧 레코드 생성 시 설정되며, 사용 시 감소
     * 만료 시 남은 크레딧량은 0으로 설정
     */
    @Column(nullable = false)
    private Double creditAmount;

    /**
     * 통화
     */
    @Column(nullable = false, length = 10)
    private String currency;


    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 크레딧 만료일
     */
    @Column(nullable = false)
    private LocalDate expireAt;

    /**
     * 크레딧이 만료되었는지 확인
     */
    public boolean isExpired() {
        return expireAt.isBefore(LocalDate.now());
    }

    /**
     * 크레딧 사용 (크레딧량 감소)
     * 
     * @param amount 사용하려는 금액
     * @return 사용하지 못한 남은 금액. 잔여 크레딧량이 충분하면 0.0을 반환하고, 부족하면 남은 금액(amount - 사용한 금액)을 반환. 유효하지 않은 입력(null 또는 0 이하)은 null 반환
     */
    public Double useCredit(Double amount) {
        if (amount == null || amount <= 0) {
            return null;
        }
        
        if (amount <= this.creditAmount) {
            // 잔여 크레딧량이 충분한 경우: 전부 사용
            this.creditAmount -= amount;
            return 0.0; // 남은 금액 없음
        } else {
            // 잔여 크레딧량이 부족한 경우: 남은 크레딧량만 사용
            Double usedAmount = this.creditAmount;
            this.creditAmount = 0.0;
            return amount - usedAmount; // 사용하지 못한 남은 금액 반환
        }
    }
}

