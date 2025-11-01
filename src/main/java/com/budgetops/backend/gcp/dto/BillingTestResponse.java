package com.budgetops.backend.gcp.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BillingTestResponse {
    private boolean ok;
    private boolean datasetExists;
    private String latestTable;
    private String message;

    public boolean isOk() {
        return ok;
    }

    public boolean isDatasetExists() {
        return datasetExists;
    }
}


