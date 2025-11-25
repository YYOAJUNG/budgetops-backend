package com.budgetops.backend.ncp.entity;

import com.budgetops.backend.aws.support.CryptoStringConverter;
import com.budgetops.backend.domain.user.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ncp_account", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ncp_access_key", columnNames = "accessKey")
})
@Getter
@Setter
public class NcpAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String accessKey;

    // DB에는 암호문만 저장
    @Convert(converter = CryptoStringConverter.class)
    @Column(nullable = false, length = 2048)
    private String secretKeyEnc;

    private String secretKeyLast4;   // 마스킹용

    private String regionCode; // KR, JP, US 등

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member owner;

    private Boolean active = Boolean.TRUE; // 등록 즉시 활성

    @PrePersist
    @PreUpdate
    private void ensureOwnerAssigned() {
        if (owner == null) {
            throw new IllegalStateException("Owner must be assigned to NCP account before persisting.");
        }
    }
}
