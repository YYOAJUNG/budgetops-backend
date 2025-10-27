package com.budgetops.backend.aws.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AwsAccountDto {
    private Long id;
    private String accountId;
    private String accountName;
    private String region;
    private String description;
    private Boolean isActive;
}
