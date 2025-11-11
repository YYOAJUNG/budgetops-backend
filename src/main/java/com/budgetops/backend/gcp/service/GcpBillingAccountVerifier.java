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
            // GCP 빌링 내보내기 테이블 이름 패턴:
            // - gcp_billing_export_v1_XXXXXX_XXXXXX_XXXXXX
            // - gcp_billing_export_v1_XXXXXX_XXXXXX_XXXXXX_YYYYMMDD
            // - 또는 다른 형식일 수 있음
            
            Page<Table> tables = bigquery.listTables(datasetId);
            Table latest = null;
            java.util.List<String> foundTableNames = new java.util.ArrayList<>();
            
            for (Table entry : tables.iterateAll()) {
                String tableId = entry.getTableId().getTable();
                foundTableNames.add(tableId);
                
                // 테이블 이름이 주어진 billingAccountId와 일치하는지 확인
                if (isBillingTableForAccount(tableId, billingIdForTable)) {
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
            
            // 디버깅: 테이블을 찾지 못한 경우 발견된 테이블 이름들을 메시지에 포함
            if (latest == null && !foundTableNames.isEmpty()) {
                String availableTables = String.join(", ", foundTableNames.subList(0, Math.min(10, foundTableNames.size())));
                res.setMessage("Dataset은 존재하지만 청구 내보내기 테이블을 찾지 못했습니다. " +
                    "입력한 빌링 계정 ID: " + billingAccountId + " (테이블 형식: " + billingIdForTable + "). " +
                    "발견된 테이블: " + availableTables + 
                    (foundTableNames.size() > 10 ? " (총 " + foundTableNames.size() + "개)" : "") + 
                    ". 내보내기 대상 결제 계정 및 권한을 확인하세요.");
            }

            res.setDatasetExists(true);
            if (latest != null) {
                res.setOk(true);
                res.setLatestTable(DATASET_NAME + "." + latest.getTableId().getTable());
                res.setMessage("billing 내보내기 확인 성공");
            } else {
                res.setOk(false);
                res.setLatestTable(null);
                // 메시지는 위에서 이미 설정됨 (테이블 목록 포함)
                if (res.getMessage() == null || res.getMessage().isEmpty()) {
                    res.setMessage("Dataset은 존재하지만 청구 내보내기 테이블을 찾지 못했습니다. 내보내기 대상 결제 계정 및 권한을 확인하세요.");
                }
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
    
    /**
     * 테이블 이름이 주어진 billingAccountId와 일치하는지 확인
     * 여러 패턴을 지원:
     * - gcp_billing_export_v1_XXXXXX_XXXXXX_XXXXXX
     * - gcp_billing_export_v1_XXXXXX_XXXXXX_XXXXXX_YYYYMMDD
     * - gcp_billing_export_XXXXXX_XXXXXX_XXXXXX
     */
    private boolean isBillingTableForAccount(String tableName, String billingIdForTable) {
        // billingIdForTable 형식: XXXXXX_XXXXXX_XXXXXX (하이픈이 언더스코어로 변환됨)
        // 테이블 이름에 이 패턴이 포함되어 있는지 확인
        
        // 직접 포함 여부 확인
        if (tableName.contains(billingIdForTable)) {
            // 추가 검증: billingIdForTable이 단독으로 나타나는지 확인
            // (다른 billingAccountId의 일부가 아닌지)
            int index = tableName.indexOf(billingIdForTable);
            // 앞뒤로 알파벳/숫자가 아닌 문자(또는 시작/끝)인지 확인
            if ((index == 0 || !Character.isLetterOrDigit(tableName.charAt(index - 1))) &&
                (index + billingIdForTable.length() >= tableName.length() || 
                 !Character.isLetterOrDigit(tableName.charAt(index + billingIdForTable.length())))) {
                return true;
            }
        }
        
        // 패턴 매칭: gcp_billing_export_v1_XXXXXX_XXXXXX_XXXXXX
        if (tableName.startsWith("gcp_billing_export_v1_")) {
            String afterPrefix = tableName.substring("gcp_billing_export_v1_".length());
            if (afterPrefix.startsWith(billingIdForTable)) {
                String remaining = afterPrefix.substring(billingIdForTable.length());
                // remaining이 비어있거나 언더스코어로 시작 (날짜 부분)
                return remaining.isEmpty() || remaining.startsWith("_");
            }
        }
        
        // 패턴 매칭: gcp_billing_export_XXXXXX_XXXXXX_XXXXXX (v1 없이)
        if (tableName.startsWith("gcp_billing_export_")) {
            String afterPrefix = tableName.substring("gcp_billing_export_".length());
            if (afterPrefix.startsWith(billingIdForTable)) {
                String remaining = afterPrefix.substring(billingIdForTable.length());
                return remaining.isEmpty() || remaining.startsWith("_");
            }
        }
        
        return false;
    }
}


