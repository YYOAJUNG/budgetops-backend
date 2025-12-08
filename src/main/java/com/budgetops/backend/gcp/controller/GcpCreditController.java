package com.budgetops.backend.gcp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GCP 크레딧 관리 컨트롤러
 * 
 * 크레딧은 추가 이력으로 관리되며, 각 추가마다 만료일 존재
 * - GET /api/gcp/accounts/{accountId}/credits - 특정 계정 크레딧 목록 조회
 * - POST /api/gcp/accounts/{accountId}/credits - 크레딧 추가 (새로운 크레딧 레코드 생성)
 * - GET /api/gcp/credits - 모든 계정 크레딧 조회
 */
@RestController
@RequestMapping("/api/gcp")
@RequiredArgsConstructor
public class GcpCreditController {

    // TODO: GcpCreditService 주입 필요
    // private final GcpCreditService creditService;

    /**
     * 특정 계정의 크레딧 목록 조회
     * GET /api/gcp/accounts/{accountId}/credits
     */
    @GetMapping("/accounts/{accountId}/credits")
    public ResponseEntity<?> getAccountCredits(
            @PathVariable Long accountId
    ) {
        // TODO: creditService.getCredits(accountId) 구현 필요
        // 반환: 크레딧 목록 (배열) - 각 크레딧의 creditAmount, currency, createdAt, expireAt 포함
        return ResponseEntity.ok("TODO: 계정별 크레딧 목록 조회 구현 필요");
    }

    /**
     * 크레딧 추가
     * POST /api/gcp/accounts/{accountId}/credits
     * 
     * 새로운 크레딧 레코드 생성
     * 각 크레딧은 creditAmount(크레딧량)와 expireAt(만료일)을 가짐
     */
    @PostMapping("/accounts/{accountId}/credits")
    public ResponseEntity<?> addCredit(
            @PathVariable Long accountId,
            @RequestBody Object request // TODO: GcpCreditAddRequest DTO 생성 필요 (creditAmount, expireAt, currency)
    ) {
        // TODO: creditService.addCredit(accountId, request) 구현 필요
        return ResponseEntity.ok("TODO: 크레딧 추가 구현 필요");
    }

    /**
     * 모든 계정의 크레딧 조회
     * GET /api/gcp/credits
     */
    @GetMapping("/credits")
    public ResponseEntity<?> getAllAccountsCredits() {
        // TODO: creditService.getAllAccountsCredits() 구현 필요
        return ResponseEntity.ok("TODO: 전체 계정 크레딧 조회 구현 필요");
    }
}
