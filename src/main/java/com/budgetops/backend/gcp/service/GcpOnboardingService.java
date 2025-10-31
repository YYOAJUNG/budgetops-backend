package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.*;
import com.google.auth.oauth2.ServiceAccountCredentials;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
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

    public GcpOnboardingService(GcpServiceAccountVerifier serviceAccountVerifier,
                                GcpBillingVerifier billingVerifier) {
        this.serviceAccountVerifier = serviceAccountVerifier;
        this.billingVerifier = billingVerifier;
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
        SaveIntegrationResponse res = new SaveIntegrationResponse();
        res.setOk(true);
        res.setIntegrationId("temp-integration-id");
        res.setMessage("Integration saved (skeleton)");
        return res;
    }
}


