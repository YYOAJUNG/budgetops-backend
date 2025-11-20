package com.budgetops.backend.gcp.entity;

import com.budgetops.backend.aws.support.CryptoStringConverter;
import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

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

    @PrePersist
    @PreUpdate
    private void ensureOwnerAssigned() {
        if (owner == null) {
            throw new IllegalStateException("Owner must be assigned to GCP account before persisting.");
        }
    }
}

