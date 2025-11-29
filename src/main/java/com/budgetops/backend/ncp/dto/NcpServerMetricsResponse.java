package com.budgetops.backend.ncp.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * NCP 서버 인스턴스 메트릭 응답 DTO
 */
@Value
@Builder
public class NcpServerMetricsResponse {
    String instanceNo;
    String instanceName;
    String region;
    List<MetricDataPoint> cpuUtilization;
    List<MetricDataPoint> networkIn;
    List<MetricDataPoint> networkOut;
    List<MetricDataPoint> diskRead;
    List<MetricDataPoint> diskWrite;
    List<MetricDataPoint> fileSystemUtilization;

    @Value
    @Builder
    public static class MetricDataPoint {
        String timestamp;
        Double value;
        String unit; // Percent, Bytes, Bytes/Second, bits/sec 등
    }
}
