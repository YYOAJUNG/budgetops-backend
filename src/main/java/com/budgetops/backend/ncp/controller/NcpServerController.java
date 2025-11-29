package com.budgetops.backend.ncp.controller;

import com.budgetops.backend.ncp.dto.NcpServerInstanceResponse;
import com.budgetops.backend.ncp.dto.NcpServerMetricsResponse;
import com.budgetops.backend.ncp.service.NcpServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ncp/accounts")
@RequiredArgsConstructor
public class NcpServerController {

    private final NcpServerService serverService;

    /**
     * 서버 인스턴스 목록 조회
     */
    @GetMapping("/{accountId}/servers/instances")
    public ResponseEntity<List<NcpServerInstanceResponse>> listInstances(
            @PathVariable Long accountId,
            @RequestParam(value = "regionCode", required = false) String regionCode
    ) {
        List<NcpServerInstanceResponse> instances = serverService.listInstances(accountId, regionCode);
        return ResponseEntity.ok(instances);
    }

    /**
     * 서버 인스턴스 시작
     */
    @PostMapping("/{accountId}/servers/instances/start")
    public ResponseEntity<List<NcpServerInstanceResponse>> startInstances(
            @PathVariable Long accountId,
            @RequestBody List<String> serverInstanceNos,
            @RequestParam(value = "regionCode", required = false) String regionCode
    ) {
        List<NcpServerInstanceResponse> instances = serverService.startInstances(accountId, serverInstanceNos, regionCode);
        return ResponseEntity.ok(instances);
    }

    /**
     * 서버 인스턴스 정지
     */
    @PostMapping("/{accountId}/servers/instances/stop")
    public ResponseEntity<List<NcpServerInstanceResponse>> stopInstances(
            @PathVariable Long accountId,
            @RequestBody List<String> serverInstanceNos,
            @RequestParam(value = "regionCode", required = false) String regionCode
    ) {
        List<NcpServerInstanceResponse> instances = serverService.stopInstances(accountId, serverInstanceNos, regionCode);
        return ResponseEntity.ok(instances);
    }

    /**
     * 서버 인스턴스 메트릭 조회
     */
    @GetMapping("/{accountId}/servers/instances/{instanceNo}/metrics")
    public ResponseEntity<NcpServerMetricsResponse> getInstanceMetrics(
            @PathVariable Long accountId,
            @PathVariable String instanceNo,
            @RequestParam(value = "regionCode", required = false) String regionCode,
            @RequestParam(value = "hours", required = false, defaultValue = "1") Integer hours
    ) {
        NcpServerMetricsResponse metrics = serverService.getInstanceMetrics(accountId, instanceNo, regionCode, hours);
        return ResponseEntity.ok(metrics);
    }
}
