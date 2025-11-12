package com.budgetops.backend.azure.controller;

import com.budgetops.backend.azure.dto.AzureAccountCreateRequest;
import com.budgetops.backend.azure.dto.AzureAccountResponse;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.service.AzureAccountService;
import com.budgetops.backend.azure.service.AzureCostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/azure/accounts")
@RequiredArgsConstructor
public class AzureAccountController {

    private final AzureAccountService accountService;
    private final AzureCostService costService;

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody AzureAccountCreateRequest request) {
        AzureAccount saved = accountService.createWithVerify(request);
        return ResponseEntity.ok(new Object() {
            public final Long id = saved.getId();
            public final String name = saved.getName();
            public final String subscriptionId = saved.getSubscriptionId();
            public final String tenantId = saved.getTenantId();
            public final String clientId = saved.getClientId();
            public final String clientSecretLast4 = "****" + (saved.getClientSecretLast4() != null ? saved.getClientSecretLast4() : "");
            public final boolean active = Boolean.TRUE.equals(saved.getActive());
        });
    }

    @GetMapping
    public ResponseEntity<List<AzureAccountResponse>> getActiveAccounts() {
        return ResponseEntity.ok(accountService.getActiveAccounts().stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{accountId}/info")
    public ResponseEntity<AzureAccountResponse> getAccountInfo(@PathVariable Long accountId) {
        AzureAccount account = accountService.getAccountInfo(accountId);
        return ResponseEntity.ok(toResponse(account));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deactivate(@PathVariable Long accountId) {
        accountService.deactivateAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}/costs")
    public ResponseEntity<List<AzureCostService.DailyCost>> getAccountCosts(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<AzureCostService.DailyCost> costs = costService.getCosts(
                accountId,
                startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        );
        return ResponseEntity.ok(costs);
    }

    @GetMapping("/{accountId}/costs/monthly")
    public ResponseEntity<AzureCostService.MonthlyCost> getMonthlyCost(
            @PathVariable Long accountId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(costService.getMonthlyCost(accountId, year, month));
    }

    @GetMapping("/costs")
    public ResponseEntity<List<AzureCostService.AccountCost>> getAllCosts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<AzureCostService.AccountCost> costs = costService.getAllAccountsCosts(
                startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        );
        return ResponseEntity.ok(costs);
    }

    private AzureAccountResponse toResponse(AzureAccount account) {
        return AzureAccountResponse.builder()
                .id(account.getId())
                .name(account.getName())
                .subscriptionId(account.getSubscriptionId())
                .tenantId(account.getTenantId())
                .clientId(account.getClientId())
                .clientSecretLast4("****" + (account.getClientSecretLast4() != null ? account.getClientSecretLast4() : ""))
                .active(Boolean.TRUE.equals(account.getActive()))
                .build();
    }
}

