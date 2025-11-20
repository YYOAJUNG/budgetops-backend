package com.budgetops.backend.azure.entity;

import com.budgetops.backend.aws.support.CryptoStringConverter;
import com.budgetops.backend.billing.entity.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    private Boolean active = Boolean.TRUE;

    @PrePersist
    @PreUpdate
    private void ensureWorkspaceAssigned() {
        if (workspace == null) {
            throw new IllegalStateException("Workspace must be assigned to Azure account before persisting.");
        }
    }
}

