package com.budgetops.backend.azure.controller;

import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
import com.budgetops.backend.azure.service.AzureComputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/azure/accounts/{accountId}/virtual-machines")
@RequiredArgsConstructor
public class AzureComputeController {

    private final AzureComputeService computeService;

    @GetMapping
    public ResponseEntity<List<AzureVirtualMachineResponse>> listVirtualMachines(
            @PathVariable Long accountId,
            @RequestParam(required = false) String location
    ) {
        return ResponseEntity.ok(computeService.listVirtualMachines(accountId, getCurrentMemberId(), location));
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}

