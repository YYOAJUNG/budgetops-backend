package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
public class GcpAccountResponse {
    private Long id;
    private String name; // 사용자가 입력한 계정 이름
    private String serviceAccountName;  // serviceAccountId @ 앞부분 (예: "budgetops")
    // private String projectName;  // TODO: 프로젝트 이름 부분 (예: "My First Project")
    private String projectId;   // projectId (예: "elated-bison-476314-f8")
    private Instant createdAt;
    private Boolean hasCredit;
    private Double creditLimitAmount;
}


