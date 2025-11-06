package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.entity.AwsResource;
import com.budgetops.backend.aws.service.AwsResourceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aws")
@RequiredArgsConstructor
public class AwsResourceController {

    private final AwsResourceQueryService service;

    // 특정 계정의 모든 리소스
    @GetMapping("/accounts/{accountId}/resources")
    public ResponseEntity<List<AwsResource>> byAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.findByAccount(accountId));
    }

    // 리소스 타입별 조회
    @GetMapping("/resources")
    public ResponseEntity<List<AwsResource>> byType(@RequestParam String resourceType) {
        return ResponseEntity.ok(service.findByType(resourceType));
    }

    // 계정 + 타입 조회
    @GetMapping("/accounts/{accountId}/resources/{resourceType}")
    public ResponseEntity<List<AwsResource>> byAccountAndType(@PathVariable Long accountId, @PathVariable String resourceType) {
        return ResponseEntity.ok(service.findByAccountAndType(accountId, resourceType));
    }
}


