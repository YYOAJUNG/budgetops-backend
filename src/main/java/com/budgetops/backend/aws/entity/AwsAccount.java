package com.budgetops.backend.aws.entity;

import com.budgetops.backend.aws.support.CryptoStringConverter;
import com.budgetops.backend.billing.entity.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "aws_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_aws_access_key_id", columnNames = "accessKeyId")
})
@Getter
@Setter
public class AwsAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String defaultRegion;

    @Column(nullable = false, length = 32)
    private String accessKeyId;

    // DB에는 암호문만 저장
    @Convert(converter = CryptoStringConverter.class)
    @Column(nullable = false, length = 2048)
    private String secretKeyEnc;

    private String secretKeyLast4;   // 마스킹용

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    private Boolean active = Boolean.TRUE; // 등록 즉시 활성(또는 UNVERIFIED 대체 가능)

    @PrePersist
    @PreUpdate
    private void ensureWorkspaceAssigned() {
        if (workspace == null) {
            throw new IllegalStateException("Workspace must be assigned to AWS account before persisting.");
        }
    }
}
