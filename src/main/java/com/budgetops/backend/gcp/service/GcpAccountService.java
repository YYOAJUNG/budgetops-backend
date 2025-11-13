package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.*;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.repository.GcpResourceRepository;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class GcpAccountService {

    private static class TempState {
        private String serviceAccountId;
        private String serviceAccountKeyJson;
        private String billingAccountId;
    }

    private final AtomicReference<TempState> tempStateRef = new AtomicReference<>(new TempState());
    private final GcpServiceAccountVerifier serviceAccountVerifier;
    private final GcpBillingAccountVerifier billingVerifier;
    private final GcpAccountRepository gcpAccountRepository;
    private final GcpResourceRepository gcpResourceRepository;

    public GcpAccountService(GcpServiceAccountVerifier serviceAccountVerifier,
                             GcpBillingAccountVerifier billingVerifier,
                             GcpAccountRepository gcpAccountRepository,
                             GcpResourceRepository gcpResourceRepository) {
        this.serviceAccountVerifier = serviceAccountVerifier;
        this.billingVerifier = billingVerifier;
        this.gcpAccountRepository = gcpAccountRepository;
        this.gcpResourceRepository = gcpResourceRepository;
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
            res.setMessage("서비스 계정 정보가 없습니다. 이전 단계를 완료해주세요.");
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

            GcpAccount entity = new GcpAccount();
            entity.setServiceAccountId(s.serviceAccountId);
            entity.setProjectId(projectId);
            entity.setBillingAccountId(s.billingAccountId);
            entity.setBillingExportDatasetId(datasetIdStr);
            entity.setBillingExportLocation(datasetLocation);
            entity.setEncryptedServiceAccountKey(s.serviceAccountKeyJson);

            GcpAccount saved = gcpAccountRepository.save(entity);

            // 완료 후 임시 상태 초기화
            tempStateRef.set(new TempState());

            res.setOk(true);
            res.setId(saved.getId());
            res.setServiceAccountId(saved.getServiceAccountId());
            res.setProjectId(saved.getProjectId());
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

    public List<GcpAccountResponse> listAccounts() {
        return gcpAccountRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAccount(Long id) {
        GcpAccount account = gcpAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GCP 계정을 찾을 수 없습니다."));
        
        // 계정에 연결된 모든 리소스를 먼저 삭제 (외래키 제약조건 해결)
        gcpResourceRepository.findByGcpAccountId(id).forEach(resource -> {
            gcpResourceRepository.delete(resource);
        });
        
        // 리소스 삭제 후 계정 삭제
        gcpAccountRepository.delete(account);
    }

    private GcpAccountResponse toResponse(GcpAccount account) {
        GcpAccountResponse response = new GcpAccountResponse();
        response.setId(account.getId());
        response.setProjectId(account.getProjectId());
        response.setCreatedAt(account.getCreatedAt());

        // serviceAccountId 파싱: "budgetops@elated-bison-476314-f8.iam.gserviceaccount.com"
        // @ 앞부분만 추출하여 serviceAccountName으로 설정
        String serviceAccountId = account.getServiceAccountId();
        if (serviceAccountId != null && serviceAccountId.contains("@")) {
            String[] parts = serviceAccountId.split("@", 2);
            response.setServiceAccountName(parts[0]);  // "budgetops"
        } else {
            response.setServiceAccountName(serviceAccountId);
        }

        return response;
    }
}


