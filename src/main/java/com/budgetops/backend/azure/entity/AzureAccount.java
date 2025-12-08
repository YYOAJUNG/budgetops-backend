package com.budgetops.backend.azure.entity;

import com.budgetops.backend.aws.support.CryptoStringConverter;
import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "azure_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_azure_client_id_subscription", columnNames = {"clientId", "subscriptionId"})
})
@Getter
@Setter
public class AzureAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subscriptionId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String clientId;

    @Convert(converter = CryptoStringConverter.class)
    @Column(nullable = false, length = 2048)
    private String clientSecretEnc;

    private String clientSecretLast4;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member owner;

    private Boolean active = Boolean.TRUE;

    /**
     * 이 계정이 Azure 크레딧(프리티어)을 사용 중인지 여부.
     * false인 경우 크레딧 사용량 계산을 하지 않습니다.
     */
    @Column(nullable = false)
    private Boolean hasCredit = Boolean.TRUE;

    /**
     * 크레딧 한도 금액 (통화 단위는 creditCurrency 기준)
     * null이면 기본 한도(AzureFreeTierLimits.AZURE_SIGNUP_CREDIT_USD)를 사용합니다.
     */
    private Double creditLimitAmount;

    /**
     * 크레딧 통화 (예: USD, KRW). null이면 비용 API에서 반환된 통화를 사용합니다.
     */
    @Column(length = 8)
    private String creditCurrency;

    /**
     * 크레딧 유효 시작일 (포털에서 제공되는 적용 날짜)
     */
    private LocalDate creditStartDate;

    /**
     * 크레딧 유효 종료일 (포털에서 제공되는 만료 날짜)
     */
    private LocalDate creditEndDate;

    @PrePersist
    @PreUpdate
    private void ensureOwnerAssigned() {
        if (owner == null) {
            throw new IllegalStateException("Owner must be assigned to Azure account before persisting.");
        }
    }
}
