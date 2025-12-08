package com.budgetops.backend.gcp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.budgetops.backend.gcp.dto.GcpAccountResponse;
import com.budgetops.backend.gcp.dto.SaveIntegrationRequest;
import com.budgetops.backend.gcp.dto.SaveIntegrationResponse;
import com.budgetops.backend.gcp.dto.TestIntegrationRequest;
import com.budgetops.backend.gcp.dto.TestIntegrationResponse;
import com.budgetops.backend.gcp.service.GcpAccountService;
import com.budgetops.backend.gcp.service.GcpFreeTierService;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/gcp/accounts")
public class GcpAccountController {

    private final GcpAccountService accountService;
    private final GcpFreeTierService freeTierService;

    public GcpAccountController(GcpAccountService accountService, GcpFreeTierService freeTierService) {
        this.accountService = accountService;
        this.freeTierService = freeTierService;
    }

    @GetMapping
    public ResponseEntity<List<GcpAccountResponse>> listAccounts() {
        return ResponseEntity.ok(accountService.listAccounts(getCurrentMemberId()));
    }

    @PostMapping("/test")
    public ResponseEntity<TestIntegrationResponse> testIntegration(@RequestBody TestIntegrationRequest request) {
        try {
            TestIntegrationResponse result = accountService.testIntegration(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<SaveIntegrationResponse> saveIntegration(@RequestBody SaveIntegrationRequest request) {
        SaveIntegrationResponse result = accountService.saveIntegration(request, getCurrentMemberId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        try {
            accountService.deleteAccount(id, getCurrentMemberId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 특정 GCP 계정의 프리티어/크레딧 사용량 조회
     * GET /api/gcp/accounts/{accountId}/freetier/usage
     */
    @GetMapping("/{accountId}/freetier/usage")
    public ResponseEntity<GcpFreeTierService.FreeTierUsage> getFreeTierUsage(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Double creditLimit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creditStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creditEnd
    ) {
        LocalDate now = LocalDate.now();
        if (startDate == null) {
            startDate = now.minusDays(30);
        }
        if (endDate == null) {
            endDate = now.plusDays(1);
        }

        GcpFreeTierService.FreeTierUsage usage = freeTierService.getFreeTierUsage(
                accountId,
                getCurrentMemberId(),
                startDate,
                endDate,
                creditLimit,
                creditStart,
                creditEnd
        );
        return ResponseEntity.ok(usage);
    }

    private Long getCurrentMemberId() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        
        if (principal instanceof Long) {
            return (Long) principal;
        } else if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid member ID format: " + principal);
            }
        } else {
            throw new IllegalStateException("Unexpected principal type: " + principal.getClass().getName());
        }
    }
}


