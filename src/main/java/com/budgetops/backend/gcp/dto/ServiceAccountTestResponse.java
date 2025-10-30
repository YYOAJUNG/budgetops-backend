package com.budgetops.backend.gcp.dto;

import java.util.List;

public class ServiceAccountTestResponse {
    private boolean ok;
    private List<String> missingRoles;
    private String message;

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
}


