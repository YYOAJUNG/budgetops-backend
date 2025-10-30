package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.ServiceAccountTestResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GcpServiceAccountVerifier {

    public ServiceAccountTestResponse verifyServiceAccount(String serviceAccountId, String serviceAccountKeyJson) {
        ServiceAccountTestResponse res = new ServiceAccountTestResponse();
        if (serviceAccountId == null || serviceAccountId.isBlank() || serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            res.setOk(false);
            res.setMessage("service account id 또는 key json이 비어 있습니다.");
            res.setMissingRoles(List.of("roles/viewer", "roles/monitoring.viewer", "roles/bigquery.dataViewer", "roles/bigquery.jobUser"));
            return res;
        }

        // 스켈레톤: 실제 GCP 호출 없이 형식만 통과
        res.setOk(true);
        res.setMessage("service account 테스트 통과 (skeleton)");
        res.setMissingRoles(new ArrayList<>());
        return res;
    }
}


