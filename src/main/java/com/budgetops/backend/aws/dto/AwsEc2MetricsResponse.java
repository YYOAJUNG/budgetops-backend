package com.budgetops.backend.aws.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class AwsEc2MetricsResponse {
    String instanceId;
    String region;
    List<MetricDataPoint> cpuUtilization;
    List<MetricDataPoint> networkIn;
    List<MetricDataPoint> networkOut;
    List<MetricDataPoint> memoryUtilization; // CloudWatch Agent가 설치된 경우에만 사용 가능
    
    @Value
    @Builder
    public static class MetricDataPoint {
        String timestamp;
        Double value;
        String unit; // Percent, Bytes, Bytes/Second 등
    }
}

