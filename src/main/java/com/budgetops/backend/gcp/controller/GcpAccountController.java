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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.budgetops.backend.gcp.dto.GcpAccountResponse;
import com.budgetops.backend.gcp.dto.SaveIntegrationRequest;
import com.budgetops.backend.gcp.dto.SaveIntegrationResponse;
import com.budgetops.backend.gcp.dto.TestIntegrationRequest;
import com.budgetops.backend.gcp.dto.TestIntegrationResponse;
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
            // 테스트 및 일관된 상태 코드를 위해 예외 대신 명시적으로 404를 반환합니다.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}


