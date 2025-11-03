package com.budgetops.backend.aws.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AwsAccountResponse {
    private Long id;
    private String name;
    private String defaultRegion;
    private String accessKeyId;
    private String secretKeyLast4;
    private boolean active;
}


