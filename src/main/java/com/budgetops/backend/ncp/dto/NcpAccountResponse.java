package com.budgetops.backend.ncp.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NcpAccountResponse {
    private Long id;
    private String name;
    private String regionCode;
    private String accessKey;
    private String secretKeyLast4;
    private boolean active;
}
