package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.BillingTestResponse;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.api.gax.paging.Page;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
public class GcpBillingAccountVerifier {

    private static final String DATASET_NAME = "billing_export_dataset";

    public BillingTestResponse verifyBilling(String billingAccountId, String serviceAccountKeyJson) {
        BillingTestResponse res = new BillingTestResponse();
        if (billingAccountId == null || billingAccountId.isBlank() || serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            res.setOk(false);
            res.setDatasetExists(false);
            res.setLatestTable(null);
            res.setMessage("billing account id 또는 key json이 비어 있습니다.");
            return res;
        }

        try {
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountKeyJson.getBytes())
            );
            String projectId = credentials.getProjectId();

            BigQuery bigquery = BigQueryOptions.newBuilder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build()
                    .getService();

            DatasetId datasetId = DatasetId.of(projectId, DATASET_NAME);
            Dataset dataset = bigquery.getDataset(datasetId);
            if (dataset == null) {
                res.setOk(false);
                res.setDatasetExists(false);
                res.setLatestTable(null);
                res.setMessage("Dataset '" + DATASET_NAME + "' 이(가) 존재하지 않습니다. GCP 콘솔에서 내보내기 설정을 확인하세요.");
                return res;
            }

            String billingIdForTable = billingAccountId.replace('-', '_');
            String tablePrefixV1 = "gcp_billing_export_v1_" + billingIdForTable;
            String tablePrefixAll = "gcp_billing_export_"; // 백업: 패턴이 다른 경우도 포괄

            Page<Table> tables = bigquery.listTables(datasetId);
            Table latest = null;
            for (Table entry : tables.iterateAll()) {
                String tableId = entry.getTableId().getTable();
                if (tableId.startsWith(tablePrefixV1) || tableId.startsWith(tablePrefixAll)) {
                    TableId fullId = TableId.of(projectId, DATASET_NAME, tableId);
                    Table full = bigquery.getTable(fullId);
                    if (full != null) {
                        if (latest == null) {
                            latest = full;
                        } else {
                            long lhs = latest.getLastModifiedTime() == null ? 0L : latest.getLastModifiedTime();
                            long rhs = full.getLastModifiedTime() == null ? 0L : full.getLastModifiedTime();
                            if (rhs > lhs) {
                                latest = full;
                            }
                        }
                    }
                }
            }

            res.setDatasetExists(true);
            if (latest != null) {
                res.setOk(true);
                res.setLatestTable(DATASET_NAME + "." + latest.getTableId().getTable());
                res.setMessage("billing 내보내기 확인 성공");
            } else {
                res.setOk(false);
                res.setLatestTable(null);
                res.setMessage("Dataset은 존재하지만 청구 내보내기 테이블을 찾지 못했습니다. 내보내기 대상 결제 계정 및 권한을 확인하세요.");
            }
            return res;
        } catch (Exception e) {
            res.setOk(false);
            res.setDatasetExists(false);
            res.setLatestTable(null);
            res.setMessage("billing 테스트 실패: " + e.getMessage());
            return res;
        }
    }
}


