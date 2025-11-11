package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestIntegrationResponse {
    private boolean ok;
    private ServiceAccountTestResult serviceAccount;
    private BillingTestResult billing;
    private String message;

    @Getter
    @Setter
    public static class ServiceAccountTestResult {
        private boolean ok;
        private java.util.List<String> missingRoles;
        private String message;
        private Integer httpStatus;
        private String debugBodySnippet;
        private java.util.List<String> grantedPermissions;
    }

    @Getter
    @Setter
    public static class BillingTestResult {
        private boolean ok;
        private boolean datasetExists;
        private String latestTable;
        private String message;
    }
}

