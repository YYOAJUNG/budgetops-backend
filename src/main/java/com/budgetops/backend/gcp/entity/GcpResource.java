package com.budgetops.backend.gcp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gcp_account_id")
    private GcpAccount gcpAccount;
}

