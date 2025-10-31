package com.budgetops.backend.gcp.dto;

import java.util.List;

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

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public List<String> getMissingRoles() {
        return missingRoles;
    }

    public void setMissingRoles(List<String> missingRoles) {
        this.missingRoles = missingRoles;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // optional
    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getDebugBodySnippet() {
        return debugBodySnippet;
    }

    public void setDebugBodySnippet(String debugBodySnippet) {
        this.debugBodySnippet = debugBodySnippet;
    }

    public List<String> getGrantedPermissions() {
        return grantedPermissions;
    }

    public void setGrantedPermissions(List<String> grantedPermissions) {
        this.grantedPermissions = grantedPermissions;
    }
}


