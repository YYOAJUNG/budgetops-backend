package com.budgetops.backend.azure.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * Azure Virtual Machine 메트릭 응답 DTO
 */
@Value
@Builder
public class AzureVmMetricsResponse {
    String vmName;
    String resourceGroup;
    List<MetricDataPoint> cpuUtilization;
    List<MetricDataPoint> networkIn;
    List<MetricDataPoint> networkOut;
    List<MetricDataPoint> memoryUtilization;

    @Value
    @Builder
    public static class MetricDataPoint {
        String timestamp;
        Double value;
        String unit; // Percent, Bytes, Bytes/Second 등
    }
}

