package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.service.GcpResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gcp")
@RequiredArgsConstructor
public class GcpResourceController {

    private final GcpResourceService service;

    @GetMapping("/accounts/{accountId}/resources")
    public ResponseEntity<GcpResourceListResponse> listResources(@PathVariable Long accountId) {
        GcpResourceListResponse response = service.listResources(accountId);
        return ResponseEntity.ok(response);
    }
}

