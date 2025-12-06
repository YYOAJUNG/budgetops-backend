package com.budgetops.backend.admin.controller;

import com.budgetops.backend.admin.dto.AdminPaymentHistoryResponse;
import com.budgetops.backend.admin.dto.AdminTokenGrantRequest;
import com.budgetops.backend.admin.dto.UserListResponse;
import com.budgetops.backend.admin.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 사용자 목록 조회 (페이지네이션)
     */
    @GetMapping("/users")
    public ResponseEntity<Page<UserListResponse>> getUserList(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String search
    ) {
        Long adminId = getCurrentMemberId();
        log.info("관리자 사용자 목록 조회: adminId={}, page={}, size={}, search={}", 
                adminId, pageable.getPageNumber(), pageable.getPageSize(), search);
        
        Page<UserListResponse> users = adminService.getUserList(pageable, search);
        return ResponseEntity.ok(users);
    }

    /**
     * 전체 사용자의 결제 내역 조회
     */
    @GetMapping("/payments")
    public ResponseEntity<List<AdminPaymentHistoryResponse>> getAllPaymentHistory(
            @RequestParam(required = false) String search
    ) {
        Long adminId = getCurrentMemberId();
        log.info("관리자 결제 내역 조회: adminId={}, search={}", adminId, search);
        
        List<AdminPaymentHistoryResponse> paymentHistory = adminService.getAllPaymentHistory(search);
        return ResponseEntity.ok(paymentHistory);
    }

    /**
     * 관리자 권한으로 토큰 추가 부여
     */
    @PostMapping("/users/{userId}/tokens")
    public ResponseEntity<Integer> grantTokens(
            @PathVariable Long userId,
            @Valid @RequestBody AdminTokenGrantRequest request
    ) {
        Long adminId = getCurrentMemberId();
        log.info("관리자 토큰 부여: adminId={}, userId={}, tokens={}, reason={}",
                adminId, userId, request.getTokens(), request.getReason());
        
        int newTotalTokens = adminService.grantTokens(userId, request.getTokens(), request.getReason());
        return ResponseEntity.ok(newTotalTokens);
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}

