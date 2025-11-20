package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.dto.GcpResourceMetricsResponse;
import com.budgetops.backend.gcp.service.GcpResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gcp")
@RequiredArgsConstructor
public class GcpResourceController {

    private final GcpResourceService service;

    @GetMapping("/accounts/{accountId}/resources")
    public ResponseEntity<GcpResourceListResponse> listResources(@PathVariable Long accountId) {
        GcpResourceListResponse response = service.listResources(accountId, getCurrentMemberId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/resources")
    public ResponseEntity<List<GcpResourceListResponse>> listAllAccountsResources() {
        List<GcpResourceListResponse> responses = service.listAllAccountsResources(getCurrentMemberId());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/resources/{resourceId}/metrics")
    public ResponseEntity<GcpResourceMetricsResponse> getResourceMetrics(
            @PathVariable String resourceId,
            @RequestParam(value = "hours", required = false, defaultValue = "1") Integer hours
    ) {
        GcpResourceMetricsResponse metrics = service.getResourceMetrics(resourceId, getCurrentMemberId(), hours);
        return ResponseEntity.ok(metrics);
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}

