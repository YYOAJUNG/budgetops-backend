package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.dto.AwsAccountResponse;
import com.budgetops.backend.aws.service.AwsAccountService;
import com.budgetops.backend.aws.service.AwsCostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/aws/accounts")
@RequiredArgsConstructor
public class AwsAccountController {
    private final AwsAccountService service;
    private final AwsCostService costService;

    // STS GetCallerIdentity로 검증 후 저장
    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody AwsAccountCreateRequest req) {
        AwsAccount saved = service.createWithVerify(req);
        // secret은 응답에서 제외: 최소 정보만 반환
        return ResponseEntity.ok(new Object() {
            public final Long id = saved.getId();
            public final String name = saved.getName();
            public final String defaultRegion = saved.getDefaultRegion();
            public final String accessKeyId = saved.getAccessKeyId();
            public final String secretKeyLast4 = "****" + saved.getSecretKeyLast4();
            public final boolean active = Boolean.TRUE.equals(saved.getActive());
        });
    }

    // 활성 계정 목록
    @GetMapping
    public ResponseEntity<?> getActiveAccounts() {
        return ResponseEntity.ok(service.getActiveAccounts().stream()
                .map(this::toResp)
                .toList());
    }

    // 계정 정보(id 기준)
    @GetMapping("/{accountId}/info")
    public ResponseEntity<AwsAccountResponse> getAccountInfo(@PathVariable Long accountId) {
        AwsAccount a = service.getAccountInfo(accountId);
        return ResponseEntity.ok(toResp(a));
    }

    // 계정 비활성(삭제 대용): 참조 무결성 보존을 위해 active=false 처리
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
        service.deactivateAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    // 특정 계정의 비용 조회
    @GetMapping("/{accountId}/costs")
    public ResponseEntity<List<AwsCostService.DailyCost>> getAccountCosts(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        // 기본값: 최근 30일
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now().plusDays(1); // Cost Explorer는 endDate를 exclusive로 처리
        }
        
        List<AwsCostService.DailyCost> costs = costService.getCosts(
                accountId,
                startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        );
        return ResponseEntity.ok(costs);
    }

    // 특정 계정의 월별 비용 조회
    @GetMapping("/{accountId}/costs/monthly")
    public ResponseEntity<AwsCostService.MonthlyCost> getAccountMonthlyCost(
            @PathVariable Long accountId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        AwsCostService.MonthlyCost monthlyCost = costService.getMonthlyCost(accountId, year, month);
        return ResponseEntity.ok(monthlyCost);
    }

    // 모든 계정의 비용 조회
    @GetMapping("/costs")
    public ResponseEntity<List<AwsCostService.AccountCost>> getAllAccountsCosts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        // 기본값: 최근 30일
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now().plusDays(1);
        }
        
        List<AwsCostService.AccountCost> accountCosts = costService.getAllAccountsCosts(
                startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        );
        return ResponseEntity.ok(accountCosts);
    }

    private AwsAccountResponse toResp(AwsAccount a) {
        return AwsAccountResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .defaultRegion(a.getDefaultRegion())
                .accessKeyId(a.getAccessKeyId())
                .secretKeyLast4("****" + a.getSecretKeyLast4())
                .active(Boolean.TRUE.equals(a.getActive()))
                .build();
    }
}


