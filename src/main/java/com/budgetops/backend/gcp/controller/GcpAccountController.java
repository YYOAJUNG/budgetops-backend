package com.budgetops.backend.gcp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.budgetops.backend.gcp.dto.BillingAccountIdRequest;
import com.budgetops.backend.gcp.dto.BillingTestResponse;
import com.budgetops.backend.gcp.dto.GcpAccountResponse;
import com.budgetops.backend.gcp.dto.SaveIntegrationResponse;
import com.budgetops.backend.gcp.dto.ServiceAccountIdRequest;
import com.budgetops.backend.gcp.dto.ServiceAccountKeyUploadRequest;
import com.budgetops.backend.gcp.dto.ServiceAccountTestResponse;
import com.budgetops.backend.gcp.service.GcpAccountService;

import java.util.List;

@RestController
@RequestMapping("/api/gcp/accounts")
public class GcpAccountController {

    private final GcpAccountService accountService;

    public GcpAccountController(GcpAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<GcpAccountResponse>> listAccounts() {
        return ResponseEntity.ok(accountService.listAccounts());
    }

    @PostMapping("/service-account/id")
    public ResponseEntity<Void> setServiceAccountId(@RequestBody ServiceAccountIdRequest request) {
        accountService.setServiceAccountId(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/service-account/key")
    public ResponseEntity<Void> uploadServiceAccountKey(@RequestBody ServiceAccountKeyUploadRequest request) {
        try {
            accountService.setServiceAccountKeyJson(request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/service-account/test")
    public ResponseEntity<ServiceAccountTestResponse> testServiceAccount() {
        ServiceAccountTestResponse result = accountService.testServiceAccount();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/billing-account/id")
    public ResponseEntity<Void> setBillingAccountId(@RequestBody BillingAccountIdRequest request) {
        try {
            accountService.setBillingAccountId(request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/billing-account/test")
    public ResponseEntity<BillingTestResponse> testBilling() {
        BillingTestResponse result = accountService.testBilling();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<SaveIntegrationResponse> completeIntegration() {
        SaveIntegrationResponse result = accountService.saveIntegration();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        try {
            accountService.deleteAccount(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}


