package com.budgetops.backend.azure.controller;

import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
import com.budgetops.backend.azure.dto.AzureVmMetricsResponse;
import com.budgetops.backend.azure.service.AzureComputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        return ResponseEntity.ok(computeService.listVirtualMachines(accountId, location));
    }

    @GetMapping("/{vmName}/metrics")
    public ResponseEntity<AzureVmMetricsResponse> getVirtualMachineMetrics(
            @PathVariable Long accountId,
            @PathVariable String vmName,
            @RequestParam String resourceGroup,
            @RequestParam(required = false, defaultValue = "1") Integer hours
    ) {
        return ResponseEntity.ok(computeService.getVirtualMachineMetrics(accountId, vmName, resourceGroup, hours));
    }

    @PostMapping("/{vmName}/start")
    public ResponseEntity<Void> startVirtualMachine(
            @PathVariable Long accountId,
            @PathVariable String vmName,
            @RequestParam String resourceGroup
    ) {
        computeService.startVirtualMachine(accountId, vmName, resourceGroup);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{vmName}/stop")
    public ResponseEntity<Void> stopVirtualMachine(
            @PathVariable Long accountId,
            @PathVariable String vmName,
            @RequestParam String resourceGroup
    ) {
        computeService.stopVirtualMachine(accountId, vmName, resourceGroup);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{vmName}")
    public ResponseEntity<Void> deleteVirtualMachine(
            @PathVariable Long accountId,
            @PathVariable String vmName,
            @RequestParam String resourceGroup
    ) {
        computeService.deleteVirtualMachine(accountId, vmName, resourceGroup);
        return ResponseEntity.ok().build();
    }
}

