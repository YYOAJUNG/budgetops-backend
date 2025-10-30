package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.dto.AwsAccountResponse;
import com.budgetops.backend.aws.service.AwsAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aws/accounts")
@RequiredArgsConstructor
public class AwsAccountController {
    private final AwsAccountService service;

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


