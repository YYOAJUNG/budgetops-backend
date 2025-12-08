package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.dto.GcpResourceMetricsResponse;
import com.budgetops.backend.gcp.service.GcpResourceControlService;
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
    private final GcpResourceControlService resourceControlService;

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

    @PostMapping("/accounts/{accountId}/resources/{resourceId}/start")
    public ResponseEntity<Void> startInstance(
            @PathVariable Long accountId,
            @PathVariable String resourceId
    ) {
        resourceControlService.startInstance(accountId, resourceId, getCurrentMemberId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/accounts/{accountId}/resources/{resourceId}/stop")
    public ResponseEntity<Void> stopInstance(
            @PathVariable Long accountId,
            @PathVariable String resourceId
    ) {
        resourceControlService.stopInstance(accountId, resourceId, getCurrentMemberId());
        return ResponseEntity.accepted().build();
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}

