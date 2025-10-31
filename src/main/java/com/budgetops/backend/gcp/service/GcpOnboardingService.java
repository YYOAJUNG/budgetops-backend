package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.*;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.budgetops.backend.gcp.entity.GcpIntegration;
import com.budgetops.backend.gcp.repository.GcpIntegrationRepository;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class GcpOnboardingService {

    private static class TempState {
        private String serviceAccountId;
        private String serviceAccountKeyJson;
        private String billingAccountId;
    }

    private final AtomicReference<TempState> tempStateRef = new AtomicReference<>(new TempState());
    private final GcpServiceAccountVerifier serviceAccountVerifier;
    private final GcpBillingVerifier billingVerifier;
    private final GcpIntegrationRepository integrationRepository;

    public GcpOnboardingService(GcpServiceAccountVerifier serviceAccountVerifier,
                                GcpBillingVerifier billingVerifier,
                                GcpIntegrationRepository integrationRepository) {
        this.serviceAccountVerifier = serviceAccountVerifier;
        this.billingVerifier = billingVerifier;
        this.integrationRepository = integrationRepository;
    }

    public void setServiceAccountId(ServiceAccountIdRequest request) {
        TempState s = tempStateRef.get();
        s.serviceAccountId = request.getServiceAccountId();
    }

    public void setServiceAccountKeyJson(ServiceAccountKeyUploadRequest request) {
        // 파싱 유효성 점검. 실패 시 예외 발생(Controller에서 400 처리)
        ServiceAccountCredentials credentials = GcpCredentialParser.parse(request.getServiceAccountKeyJson());
        
        TempState s = tempStateRef.get();
        s.serviceAccountKeyJson = request.getServiceAccountKeyJson();
    }

    public ServiceAccountTestResponse testServiceAccount() {
        TempState s = tempStateRef.get();
        return serviceAccountVerifier.verifyServiceAccount(s.serviceAccountId, s.serviceAccountKeyJson);
    }

    public void setBillingAccountId(BillingAccountIdRequest request) {
        String billingIdRaw = request.getBillingAccountId();
        if (billingIdRaw == null) {
            throw new IllegalArgumentException("잘못된 결제 계정 ID 형식입니다. 예) EXAMPL-123456-ABC123");
        }
        String billingId = billingIdRaw.trim().toUpperCase();
        // 형식 예: EXAMPL-123456-ABC123
        Pattern pattern = Pattern.compile("^[A-Z0-9]{6}-[A-Z0-9]{6}-[A-Z0-9]{6}$");
        if (!pattern.matcher(billingId).matches()) {
            throw new IllegalArgumentException("잘못된 결제 계정 ID 형식입니다. 예) EXAMPL-123456-ABC123");
        }

        TempState s = tempStateRef.get();
        s.billingAccountId = billingId;
    }

    public BillingTestResponse testBilling() {
        TempState s = tempStateRef.get();
        return billingVerifier.verifyBilling(s.billingAccountId, s.serviceAccountKeyJson);
    }

    public SaveIntegrationResponse saveIntegration() {
        TempState s = tempStateRef.get();
        SaveIntegrationResponse res = new SaveIntegrationResponse();
        if (s.serviceAccountId == null || s.serviceAccountKeyJson == null) {
            res.setOk(false);
            res.setMessage("서비스 계정 정보가 없습니다. 1~3단계를 완료해주세요.");
            return res;
        }
        try {
            ServiceAccountCredentials credentials = GcpCredentialParser.parse(s.serviceAccountKeyJson);
            String projectId = credentials.getProjectId();

            // BigQuery dataset 위치 확인 (있으면 함께 저장)
            String datasetIdStr = null;
            String datasetLocation = null;
            try {
                BigQuery bq = BigQueryOptions.newBuilder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build()
                        .getService();
                DatasetId datasetId = DatasetId.of(projectId, "billing_export_dataset");
                Dataset ds = bq.getDataset(datasetId);
                if (ds != null) {
                    datasetIdStr = "billing_export_dataset";
                    datasetLocation = ds.getLocation();
                }
            } catch (Exception ignore) {
                // 권한/구성이 아직 안되었을 수 있으므로 무시하고 계속 저장
            }

            GcpIntegration entity = new GcpIntegration();
            entity.setServiceAccountId(s.serviceAccountId);
            entity.setProjectId(projectId);
            entity.setBillingAccountId(s.billingAccountId);
            entity.setBillingExportDatasetId(datasetIdStr);
            entity.setBillingExportLocation(datasetLocation);
            entity.setEncryptedServiceAccountKey(s.serviceAccountKeyJson);
            entity.setLastVerifiedAt(Instant.now());

            GcpIntegration saved = integrationRepository.save(entity);

            // 완료 후 임시 상태 초기화
            tempStateRef.set(new TempState());

            res.setOk(true);
            res.setIntegrationId(String.valueOf(saved.getId()));
            res.setMessage("Integration saved");
            return res;
        } catch (IllegalArgumentException e) {
            res.setOk(false);
            res.setMessage("서비스 계정 키 파싱 실패: " + e.getMessage());
            return res;
        } catch (Exception e) {
            res.setOk(false);
            res.setMessage("저장 실패: " + e.getMessage());
            return res;
        }
    }
}


