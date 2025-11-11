package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.dto.AwsAccountResponse;
import com.budgetops.backend.aws.service.AwsAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aws/accounts")
@RequiredArgsConstructor
public class AwsAccountController {
    private final AwsAccountService service;

    // STS GetCallerIdentity로 검증 후 저장
    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody AwsAccountCreateRequest req) {
        // SecurityContext에서 로그인한 사용자의 memberId 추출
        Long memberId = (Long) org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        AwsAccount saved = service.createWithVerify(req, memberId);
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

    // 활성 계정 목록 (로그인한 사용자의 workspace 기준)
    @GetMapping
    public ResponseEntity<?> getActiveAccounts() {
        // SecurityContext에서 로그인한 사용자의 memberId 추출
        Long memberId = (Long) org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        List<AwsAccount> accounts = service.getActiveAccountsByMember(memberId);
        return ResponseEntity.ok(accounts.stream()
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


