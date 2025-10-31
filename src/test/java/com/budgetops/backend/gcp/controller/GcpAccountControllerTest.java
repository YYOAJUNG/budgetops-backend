package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.gcp.dto.ServiceAccountIdRequest;
import com.budgetops.backend.gcp.dto.ServiceAccountKeyUploadRequest;
import com.budgetops.backend.gcp.service.GcpAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GcpAccountController.class)
class GcpAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GcpAccountService onboardingService;

    @Test
    @DisplayName("POST /api/gcp/accounts/service-account/id returns 200 and delegates to service")
    void setServiceAccountId_success() throws Exception {
        String body = "{\"serviceAccountId\":\"sa-123@project.iam.gserviceaccount.com\"}";

        mockMvc.perform(post("/api/gcp/accounts/service-account/id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(onboardingService).setServiceAccountId(argThat((ArgumentMatcher<ServiceAccountIdRequest>) req ->
                req != null && "sa-123@project.iam.gserviceaccount.com".equals(req.getServiceAccountId())
        ));
    }

    @Test
    @DisplayName("POST /api/gcp/accounts/service-account/key returns 200 when service succeeds")
    void uploadServiceAccountKey_success() throws Exception {
        String body = "{\"serviceAccountKeyJson\":\"{\\\"type\\\":\\\"service_account\\\"}\"}";

        mockMvc.perform(post("/api/gcp/accounts/service-account/key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(onboardingService).setServiceAccountKeyJson(argThat((ArgumentMatcher<ServiceAccountKeyUploadRequest>) req ->
                req != null && "{\"type\":\"service_account\"}".equals(req.getServiceAccountKeyJson())
        ));
    }

    @Test
    @DisplayName("POST /api/gcp/accounts/service-account/key returns 400 when IllegalArgumentException thrown")
    void uploadServiceAccountKey_badRequest_onIllegalArgument() throws Exception {
        String invalidJson = "{\"type\":\"not_service_account\"}";

        doThrow(new IllegalArgumentException("invalid key"))
                .when(onboardingService)
                .setServiceAccountKeyJson(argThat((ArgumentMatcher<ServiceAccountKeyUploadRequest>) req ->
                        req != null && invalidJson.equals(req.getServiceAccountKeyJson())
                ));

        String body = "{\"serviceAccountKeyJson\":\"" + invalidJson + "\"}";

        mockMvc.perform(post("/api/gcp/accounts/service-account/key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
