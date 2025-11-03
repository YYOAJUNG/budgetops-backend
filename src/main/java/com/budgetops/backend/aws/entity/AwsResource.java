package com.budgetops.backend.aws.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "aws_resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AwsResource {
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
    @JoinColumn(name = "aws_account_id")
    private AwsAccount awsAccount;
}
