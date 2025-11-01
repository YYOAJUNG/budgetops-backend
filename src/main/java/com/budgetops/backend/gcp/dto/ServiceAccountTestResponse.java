package com.budgetops.backend.gcp.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceAccountTestResponse {
    private boolean ok;
    private List<String> missingRoles;
    private String message;
    private Integer httpStatus; // optional: 최근 외부 호출 HTTP 상태
    private String debugBodySnippet; // optional: 응답 바디 일부
    private List<String> grantedPermissions; // optional: testIamPermissions 응답 결과

    public boolean isOk() {
        return ok;
    }
}


