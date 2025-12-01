package com.budgetops.backend.gcp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "gcp_resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GcpResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String resourceId;

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String resourceName;

    @Column
    private String region;

    @Column
    private String status;

    @Column
    private String description;

    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyCost;

    @Column
    private Instant lastUpdated;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> additionalAttributes; // 리소스 타입별 추가 정보

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gcp_account_id")
    private GcpAccount gcpAccount;
}

