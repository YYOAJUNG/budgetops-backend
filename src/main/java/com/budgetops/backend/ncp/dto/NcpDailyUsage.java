package com.budgetops.backend.ncp.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NcpDailyUsage {
    private String useDate; // YYYYMMDD 형식
    private String contractNo;
    private String instanceName;
    private String contractType; // SVR, STG 등
    private String productItemKind; // Server, Storage 등
    private Double usageQuantity; // 사용량
    private String unit; // 단위 코드 (USAGE_SEC 등)
    private String regionCode;
}
