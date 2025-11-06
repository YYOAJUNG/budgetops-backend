package com.budgetops.backend.gcp.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gcp_account_id")
    private GcpAccount gcpAccount;
}

