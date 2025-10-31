package com.budgetops.backend.gcp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "gcp_integration")
@EntityListeners(AuditingEntityListener.class)
public class GcpIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(length = 512)
    private String secretReference;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastVerifiedAt;
}


