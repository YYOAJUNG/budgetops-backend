package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.entity.AwsResource;
import com.budgetops.backend.aws.service.AwsResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/aws")
@RequiredArgsConstructor
public class AwsResourceController {
    
    private final AwsResourceService awsResourceService;
    
    /**
     * 모든 활성화된 AWS 계정 조회
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<AwsAccount>> getAllAccounts() {
        List<AwsAccount> accounts = awsResourceService.getAllActiveAccounts();
        return ResponseEntity.ok(accounts);
    }
    
    /**
     * 특정 AWS 계정의 모든 리소스 조회
     */
    @GetMapping("/accounts/{accountId}/resources")
    public ResponseEntity<List<AwsResource>> getResourcesByAccount(@PathVariable Long accountId) {
        List<AwsResource> resources = awsResourceService.getResourcesByAccountId(accountId);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 특정 리소스 타입의 리소스들 조회
     */
    @GetMapping("/resources")
    public ResponseEntity<List<AwsResource>> getResourcesByType(@RequestParam String resourceType) {
        List<AwsResource> resources = awsResourceService.getResourcesByType(resourceType);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * 특정 계정의 특정 리소스 타입 조회
     */
    @GetMapping("/accounts/{accountId}/resources/{resourceType}")
    public ResponseEntity<List<AwsResource>> getResourcesByAccountAndType(
            @PathVariable Long accountId, 
            @PathVariable String resourceType) {
        List<AwsResource> resources = awsResourceService.getResourcesByAccountAndType(accountId, resourceType);
        return ResponseEntity.ok(resources);
    }
    
    /**
     * AWS 계정 ID로 계정 정보 조회
     */
    @GetMapping("/accounts/{accountId}/info")
    public ResponseEntity<AwsAccount> getAccountInfo(@PathVariable String accountId) {
        Optional<AwsAccount> account = awsResourceService.getAccountByAccountId(accountId);
        return account.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }
}
