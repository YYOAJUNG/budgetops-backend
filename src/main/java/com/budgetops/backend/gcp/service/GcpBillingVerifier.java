package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.BillingTestResponse;
import org.springframework.stereotype.Service;

@Service
public class GcpBillingVerifier {

    public BillingTestResponse verifyBilling(String billingAccountId, String serviceAccountKeyJson) {
        BillingTestResponse res = new BillingTestResponse();
        if (billingAccountId == null || billingAccountId.isBlank() || serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            res.setOk(false);
            res.setDatasetExists(false);
            res.setLatestTable(null);
            res.setMessage("billing account id 또는 key json이 비어 있습니다.");
            return res;
        }

        // 스켈레톤: 실제 GCP 호출 없이 형식만 통과
        res.setOk(true);
        res.setDatasetExists(true);
        res.setLatestTable("billing_export_dataset.sample_latest_table");
        res.setMessage("billing 테스트 통과 (skeleton)");
        return res;
    }
}


