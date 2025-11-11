package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.*;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class GcpAccountService {

    private final GcpServiceAccountVerifier serviceAccountVerifier;
    private final GcpBillingAccountVerifier billingVerifier;
    private final GcpAccountRepository gcpAccountRepository;

    public GcpAccountService(GcpServiceAccountVerifier serviceAccountVerifier,
                             GcpBillingAccountVerifier billingVerifier,
                             GcpAccountRepository gcpAccountRepository) {
        this.serviceAccountVerifier = serviceAccountVerifier;
        this.billingVerifier = billingVerifier;
        this.gcpAccountRepository = gcpAccountRepository;
    }

    public ServiceAccountTestResponse testServiceAccount(ServiceAccountTestRequest request) {
        if (request.getServiceAccountId() == null || request.getServiceAccountId().isBlank()) {
            throw new IllegalArgumentException("서비스 계정 ID가 필요합니다.");
        }
        if (request.getServiceAccountKeyJson() == null || request.getServiceAccountKeyJson().isBlank()) {
            throw new IllegalArgumentException("서비스 계정 키 JSON이 필요합니다.");
        }
        
        // 파싱 유효성 점검
        GcpCredentialParser.parse(request.getServiceAccountKeyJson());
        
        return serviceAccountVerifier.verifyServiceAccount(
            request.getServiceAccountId(), 
            request.getServiceAccountKeyJson()
        );
    }

    public BillingTestResponse testBilling(BillingAccountTestRequest request) {
        if (request.getBillingAccountId() == null || request.getBillingAccountId().isBlank()) {
            throw new IllegalArgumentException("빌링 계정 ID가 필요합니다.");
        }
        if (request.getServiceAccountKeyJson() == null || request.getServiceAccountKeyJson().isBlank()) {
            throw new IllegalArgumentException("서비스 계정 키 JSON이 필요합니다.");
        }
        
        // 빌링 계정 ID 형식 검증
        String billingIdRaw = request.getBillingAccountId();
        String billingId = billingIdRaw.trim().toUpperCase();
        Pattern pattern = Pattern.compile("^[A-Z0-9]{6}-[A-Z0-9]{6}-[A-Z0-9]{6}$");
        if (!pattern.matcher(billingId).matches()) {
            throw new IllegalArgumentException("잘못된 결제 계정 ID 형식입니다. 예) EXAMPL-123456-ABC123");
        }
        
        // 파싱 유효성 점검
        GcpCredentialParser.parse(request.getServiceAccountKeyJson());
        
        return billingVerifier.verifyBilling(billingId, request.getServiceAccountKeyJson());
    }

    public TestIntegrationResponse testIntegration(TestIntegrationRequest request) {
        TestIntegrationResponse response = new TestIntegrationResponse();
        
        // 필수 필드 검증
        if (request.getServiceAccountId() == null || request.getServiceAccountId().isBlank()) {
            throw new IllegalArgumentException("서비스 계정 ID가 필요합니다.");
        }
        if (request.getServiceAccountKeyJson() == null || request.getServiceAccountKeyJson().isBlank()) {
            throw new IllegalArgumentException("서비스 계정 키 JSON이 필요합니다.");
        }
        
        // 파싱 유효성 점검
        GcpCredentialParser.parse(request.getServiceAccountKeyJson());
        
        // 1. 서비스 계정 테스트
        ServiceAccountTestResponse serviceAccountResult = serviceAccountVerifier.verifyServiceAccount(
            request.getServiceAccountId(),
            request.getServiceAccountKeyJson()
        );
        
        TestIntegrationResponse.ServiceAccountTestResult serviceAccountTest = 
            new TestIntegrationResponse.ServiceAccountTestResult();
        serviceAccountTest.setOk(serviceAccountResult.isOk());
        serviceAccountTest.setMissingRoles(serviceAccountResult.getMissingRoles());
        serviceAccountTest.setMessage(serviceAccountResult.getMessage());
        serviceAccountTest.setHttpStatus(serviceAccountResult.getHttpStatus());
        serviceAccountTest.setDebugBodySnippet(serviceAccountResult.getDebugBodySnippet());
        serviceAccountTest.setGrantedPermissions(serviceAccountResult.getGrantedPermissions());
        response.setServiceAccount(serviceAccountTest);
        
        // 2. 빌링 계정 테스트 (선택적)
        TestIntegrationResponse.BillingTestResult billingTest = 
            new TestIntegrationResponse.BillingTestResult();
        
        if (request.getBillingAccountId() != null && !request.getBillingAccountId().isBlank()) {
            // 빌링 계정 ID 형식 검증
            String billingIdRaw = request.getBillingAccountId();
            String billingId = billingIdRaw.trim().toUpperCase();
            Pattern pattern = Pattern.compile("^[A-Z0-9]{6}-[A-Z0-9]{6}-[A-Z0-9]{6}$");
            if (!pattern.matcher(billingId).matches()) {
                billingTest.setOk(false);
                billingTest.setDatasetExists(false);
                billingTest.setLatestTable(null);
                billingTest.setMessage("잘못된 결제 계정 ID 형식입니다. 예) EXAMPL-123456-ABC123");
            } else {
                BillingTestResponse billingResult = billingVerifier.verifyBilling(
                    billingId, 
                    request.getServiceAccountKeyJson()
                );
                billingTest.setOk(billingResult.isOk());
                billingTest.setDatasetExists(billingResult.isDatasetExists());
                billingTest.setLatestTable(billingResult.getLatestTable());
                billingTest.setMessage(billingResult.getMessage());
            }
        } else {
            // 빌링 계정 ID가 없으면 테스트하지 않음
            billingTest.setOk(false);
            billingTest.setDatasetExists(false);
            billingTest.setLatestTable(null);
            billingTest.setMessage("빌링 계정 ID가 제공되지 않아 테스트를 건너뜁니다.");
        }
        response.setBilling(billingTest);
        
        // 전체 결과 설정
        boolean overallOk = serviceAccountTest.isOk() && 
            (request.getBillingAccountId() == null || request.getBillingAccountId().isBlank() || billingTest.isOk());
        response.setOk(overallOk);
        
        if (overallOk) {
            if (request.getBillingAccountId() != null && !request.getBillingAccountId().isBlank()) {
                response.setMessage("서비스 계정 및 빌링 계정 테스트 성공");
            } else {
                response.setMessage("서비스 계정 테스트 성공");
            }
        } else {
            StringBuilder msg = new StringBuilder();
            if (!serviceAccountTest.isOk()) {
                msg.append("서비스 계정 테스트 실패");
            }
            if (request.getBillingAccountId() != null && !request.getBillingAccountId().isBlank() && !billingTest.isOk()) {
                if (msg.length() > 0) msg.append(", ");
                msg.append("빌링 계정 테스트 실패");
            }
            response.setMessage(msg.toString());
        }
        
        return response;
    }

    public SaveIntegrationResponse saveIntegration(SaveIntegrationRequest request) {
        SaveIntegrationResponse res = new SaveIntegrationResponse();
        
        if (request.getServiceAccountId() == null || request.getServiceAccountId().isBlank()) {
            res.setOk(false);
            res.setMessage("서비스 계정 ID가 필요합니다.");
            return res;
        }
        if (request.getServiceAccountKeyJson() == null || request.getServiceAccountKeyJson().isBlank()) {
            res.setOk(false);
            res.setMessage("서비스 계정 키 JSON이 필요합니다.");
            return res;
        }
        try {
            ServiceAccountCredentials credentials = GcpCredentialParser.parse(request.getServiceAccountKeyJson());
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

            // 빌링 계정 ID 형식 검증 및 정규화
            String billingAccountId = null;
            if (request.getBillingAccountId() != null && !request.getBillingAccountId().isBlank()) {
                String billingIdRaw = request.getBillingAccountId();
                String billingId = billingIdRaw.trim().toUpperCase();
                Pattern pattern = Pattern.compile("^[A-Z0-9]{6}-[A-Z0-9]{6}-[A-Z0-9]{6}$");
                if (!pattern.matcher(billingId).matches()) {
                    res.setOk(false);
                    res.setMessage("잘못된 결제 계정 ID 형식입니다. 예) EXAMPL-123456-ABC123");
                    return res;
                }
                billingAccountId = billingId;
            }

            GcpAccount entity = new GcpAccount();
            entity.setServiceAccountId(request.getServiceAccountId());
            entity.setProjectId(projectId);
            entity.setBillingAccountId(billingAccountId);
            entity.setBillingExportDatasetId(datasetIdStr);
            entity.setBillingExportLocation(datasetLocation);
            entity.setEncryptedServiceAccountKey(request.getServiceAccountKeyJson());

            GcpAccount saved = gcpAccountRepository.save(entity);

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


