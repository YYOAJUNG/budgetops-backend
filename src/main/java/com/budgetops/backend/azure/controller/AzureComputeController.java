package com.budgetops.backend.azure.controller;

import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
import com.budgetops.backend.azure.dto.AzureVmMetricsResponse;
import com.budgetops.backend.azure.service.AzureComputeService;
import com.budgetops.backend.azure.service.AzureMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/azure/accounts/{accountId}/virtual-machines")
@RequiredArgsConstructor
public class AzureComputeController {

    private final AzureComputeService computeService;
    private final AzureMetricsService metricsService;

    @GetMapping
    public ResponseEntity<List<AzureVirtualMachineResponse>> listVirtualMachines(
            @PathVariable Long accountId,
            @RequestParam(required = false) String location
    ) {
        return ResponseEntity.ok(computeService.listVirtualMachines(accountId, location));
    }

    @GetMapping("/{vmName}/metrics")
    public ResponseEntity<AzureVmMetricsResponse> getVirtualMachineMetrics(
            @PathVariable Long accountId,
            @PathVariable String vmName,
            @RequestParam String resourceGroup,
            @RequestParam(required = false) Integer hours
    ) {
        return ResponseEntity.ok(metricsService.getMetrics(accountId, resourceGroup, vmName, hours));
    }

    @PostMapping("/{vmName}/start")
    public ResponseEntity<Void> startVirtualMachine(
            @PathVariable Long accountId,
            @PathVariable String vmName,
            @RequestParam String resourceGroup
    ) {
        computeService.startVirtualMachine(accountId, resourceGroup, vmName);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{vmName}/stop")
    public ResponseEntity<Void> stopVirtualMachine(
            @PathVariable Long accountId,
            @PathVariable String vmName,
            @RequestParam String resourceGroup,
            @RequestParam(defaultValue = "false") boolean skipShutdown
    ) {
        computeService.stopVirtualMachine(accountId, resourceGroup, vmName, skipShutdown);
        return ResponseEntity.accepted().build();
    }
}

