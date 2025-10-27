package com.budgetops.backend.aws.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "aws_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AwsAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String accessKeyId;

    @Column(nullable = false)
    private String secretAccessKey;

    @Column
    private String description;

    @Column(nullable = false)
    private Boolean isActive = true;
}
