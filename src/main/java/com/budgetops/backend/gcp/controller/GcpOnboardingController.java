package com.budgetops.backend.gcp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.budgetops.backend.gcp.dto.BillingAccountIdRequest;
import com.budgetops.backend.gcp.dto.BillingTestResponse;
import com.budgetops.backend.gcp.dto.SaveIntegrationResponse;
import com.budgetops.backend.gcp.dto.ServiceAccountIdRequest;
import com.budgetops.backend.gcp.dto.ServiceAccountKeyUploadRequest;
import com.budgetops.backend.gcp.dto.ServiceAccountTestResponse;
import com.budgetops.backend.gcp.service.GcpOnboardingService;

@RestController
@RequestMapping("/gcp/onboarding")
public class GcpOnboardingController {

    private final GcpOnboardingService onboardingService;

    public GcpOnboardingController(GcpOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/service-account/id")
    public ResponseEntity<Void> setServiceAccountId(@RequestBody ServiceAccountIdRequest request) {
        onboardingService.setServiceAccountId(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/service-account/key")
    public ResponseEntity<Void> uploadServiceAccountKey(@RequestBody ServiceAccountKeyUploadRequest request) {
        try {
            onboardingService.setServiceAccountKeyJson(request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/service-account/test")
    public ResponseEntity<ServiceAccountTestResponse> testServiceAccount() {
        ServiceAccountTestResponse result = onboardingService.testServiceAccount();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/billing-account/id")
    public ResponseEntity<Void> setBillingAccountId(@RequestBody BillingAccountIdRequest request) {
        try {
            onboardingService.setBillingAccountId(request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/billing-account/test")
    public ResponseEntity<BillingTestResponse> testBilling() {
        BillingTestResponse result = onboardingService.testBilling();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<SaveIntegrationResponse> completeOnboarding() {
        SaveIntegrationResponse result = onboardingService.saveIntegration();
        return ResponseEntity.ok(result);
    }
}


