package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.gcp.dto.GcpAccountDailyCostsResponse;
import com.budgetops.backend.gcp.service.GcpCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * GCP 비용 조회 컨트롤러
 * RESTful URI 설계:
 * - GET /api/gcp/accounts/{accountId}/costs - 특정 계정 비용
 * - GET /api/gcp/accounts/{accountId}/costs/monthly - 월별 비용
 * - GET /api/gcp/costs - 모든 계정 비용
 */
@RestController
@RequestMapping("/api/gcp")
@RequiredArgsConstructor
public class GcpCostController {

    private final GcpCostService costService;

    /**
     * 특정 계정의 비용 조회 (일별)
     * GET /api/gcp/accounts/{accountId}/costs
     */
    @GetMapping("/accounts/{accountId}/costs")
    public ResponseEntity<GcpAccountDailyCostsResponse> getAccountCosts(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        // 기본값: 최근 30일
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now().plusDays(1); // endDate는 exclusive
        }

        GcpAccountDailyCostsResponse response = costService.getCosts(
                accountId,
                startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * TODO: 특정 계정의 월별 비용 조회
     * GET /api/gcp/accounts/{accountId}/costs/monthly
     */

    /**
     * TODO: 모든 계정의 비용 조회
     * GET /api/gcp/costs
     */
}

