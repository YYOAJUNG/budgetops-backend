package com.budgetops.backend.aws.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AwsResourceDto {
    private Long id;
    private String resourceId;
    private String resourceType;
    private String resourceName;
    private String region;
    private String status;
    private String description;
    private Long awsAccountId;
}
