package com.budgetops.backend.gcp.entity;

import com.budgetops.backend.aws.support.CryptoStringConverter;
import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "gcp_account")
@EntityListeners(AuditingEntityListener.class)
public class GcpAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 128)
    private String name; // 사용자가 입력한 계정 이름

    @Column(nullable = false, length = 320)
    private String serviceAccountId;

    @Column(nullable = false, length = 128)
    private String projectId;

    @Column(length = 128)
    private String billingAccountId;

    @Column(length = 128)
    private String billingExportProjectId;

    @Column(length = 256)
    private String billingExportDatasetId;

    @Column(length = 32)
    private String billingExportLocation;

    @Convert(converter = CryptoStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String encryptedServiceAccountKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member owner;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 이 계정이 GCP 크레딧(프리티어)을 사용 중인지 여부.
     * false인 경우 크레딧 사용량 계산을 하지 않습니다.
     */
    @Column(nullable = false)
    private Boolean hasCredit = Boolean.TRUE;

    /**
     * 크레딧 한도 금액 (통화 단위는 creditCurrency 기준)
     * null이면 기본 한도(GcpFreeTierLimits.DEFAULT_FREE_TIER_CREDIT_LIMIT)를 사용합니다.
     */
    private Double creditLimitAmount;

    /**
     * 크레딧 통화 (예: USD, KRW). null이면 Billing Export에서 반환된 통화를 사용합니다.
     */
    @Column(length = 8)
    private String creditCurrency;

    /**
     * 크레딧 유효 시작일
     */
    private LocalDate creditStartDate;

    /**
     * 크레딧 유효 종료일
     */
    private LocalDate creditEndDate;

    @PrePersist
    @PreUpdate
    private void ensureOwnerAssigned() {
        if (owner == null) {
            throw new IllegalStateException("Owner must be assigned to GCP account before persisting.");
        }
    }
}

