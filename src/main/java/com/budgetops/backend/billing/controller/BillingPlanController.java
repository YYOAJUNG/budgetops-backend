package com.budgetops.backend.billing.controller;

import com.budgetops.backend.billing.enums.BillingPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/plans")
@RequiredArgsConstructor
@Slf4j
public class BillingPlanController {

    /**
     * 사용 가능한 모든 요금제 조회
     * GET /api/v1/billing/plans
     */
    @GetMapping
    public ResponseEntity<BillingPlan[]> getAllPlans() {
        log.debug("요금제 목록 조회 요청");
        return ResponseEntity.ok(BillingPlan.values());
    }
}
