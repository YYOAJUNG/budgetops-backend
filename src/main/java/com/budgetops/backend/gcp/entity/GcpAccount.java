package com.budgetops.backend.gcp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import com.budgetops.backend.aws.support.CryptoStringConverter;
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

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}


