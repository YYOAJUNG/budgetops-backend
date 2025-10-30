package com.budgetops.backend.gcp.dto;

public class BillingTestResponse {
    private boolean ok;
    private boolean datasetExists;
    private String latestTable;
    private String message;

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public boolean isDatasetExists() {
        return datasetExists;
    }

    public void setDatasetExists(boolean datasetExists) {
        this.datasetExists = datasetExists;
    }

    public String getLatestTable() {
        return latestTable;
    }

    public void setLatestTable(String latestTable) {
        this.latestTable = latestTable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}


