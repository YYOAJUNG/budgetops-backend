package com.budgetops.backend.azure.controller;

import com.budgetops.backend.azure.dto.AzureAccountCreateRequest;
import com.budgetops.backend.azure.dto.AzureAccountResponse;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.service.AzureAccountService;
import com.budgetops.backend.azure.service.AzureCostService;
import com.budgetops.backend.azure.service.AzureFreeTierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/azure/accounts")
@RequiredArgsConstructor
public class AzureAccountController {

    private final AzureAccountService accountService;
    private final AzureCostService costService;
    private final AzureFreeTierService freeTierService;

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody AzureAccountCreateRequest request) {
        AzureAccount saved = accountService.createWithVerify(request, getCurrentMemberId());
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
        return ResponseEntity.ok(accountService.getActiveAccounts(getCurrentMemberId()).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/{accountId}/info")
    public ResponseEntity<AzureAccountResponse> getAccountInfo(@PathVariable Long accountId) {
        AzureAccount account = accountService.getAccountInfo(accountId, getCurrentMemberId());
        return ResponseEntity.ok(toResponse(account));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deactivate(@PathVariable Long accountId) {
        accountService.deactivateAccount(accountId, getCurrentMemberId());
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
                getCurrentMemberId(),
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
        return ResponseEntity.ok(costService.getMonthlyCost(accountId, getCurrentMemberId(), year, month));
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
                getCurrentMemberId(),
                startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        );
        return ResponseEntity.ok(costs);
    }

    /**
     * 특정 Azure 계정의 크레딧 기반 프리티어 사용량 조회 (근사치)
     * GET /api/azure/accounts/{accountId}/freetier/usage
     *
     * 쿼리 파라미터:
     * - startDate, endDate: 분석에 사용할 기간 (미지정 시 크레딧 기간과 동일)
     * - creditLimit: 크레딧 한도 (미지정 시 기본 200 USD 가정)
     * - creditStart, creditEnd: 크레딧 유효 기간 (미지정 시 오늘 ~ 오늘+1개월)
     */
    @GetMapping("/{accountId}/freetier/usage")
    public ResponseEntity<AzureFreeTierService.FreeTierUsage> getFreeTierUsage(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Double creditLimit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creditStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate creditEnd
    ) {
        AzureFreeTierService.FreeTierUsage usage = freeTierService.getCreditFreeTierUsage(
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

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}

