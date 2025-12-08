package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveIntegrationRequest {
    private String name; // 사용자가 입력한 계정 이름
    private String serviceAccountId;
    private String serviceAccountKeyJson;
    private String billingAccountId;
    /**
     * 이 계정이 GCP 크레딧(프리티어)을 사용 중인지 여부.
     * null이면 기본값(true)을 사용합니다.
     */
    private Boolean hasCredit;

    /**
     * 크레딧 한도 금액. null이면 기본 한도를 사용합니다.
     */
    private Double creditLimitAmount;
}

