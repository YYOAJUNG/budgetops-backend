package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
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

    // STS 검증 없이 즉시 저장
    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody AwsAccountCreateRequest req) {
        AwsAccount saved = service.createWithoutVerify(req);
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
}


