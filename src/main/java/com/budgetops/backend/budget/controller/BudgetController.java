package com.budgetops.backend.budget.controller;

import com.budgetops.backend.budget.dto.BudgetAlertResponse;
import com.budgetops.backend.budget.dto.BudgetSettingsRequest;
import com.budgetops.backend.budget.dto.BudgetSettingsResponse;
import com.budgetops.backend.budget.dto.BudgetUsageResponse;
import com.budgetops.backend.budget.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping("/me/settings")
    public ResponseEntity<BudgetSettingsResponse> getSettings() {
        return ResponseEntity.ok(budgetService.getSettings(getCurrentMemberId()));
    }

    @PutMapping("/me/settings")
    public ResponseEntity<BudgetSettingsResponse> updateSettings(
            @Valid @RequestBody BudgetSettingsRequest request
    ) {
        return ResponseEntity.ok(budgetService.updateSettings(getCurrentMemberId(), request));
    }

    @GetMapping("/me/usage")
    public ResponseEntity<BudgetUsageResponse> getUsage() {
        return ResponseEntity.ok(budgetService.getUsage(getCurrentMemberId()));
    }

    @PostMapping("/alerts/check")
    public ResponseEntity<List<BudgetAlertResponse>> checkAlerts() {
        return ResponseEntity.ok(budgetService.checkAlerts(getCurrentMemberId()));
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}

