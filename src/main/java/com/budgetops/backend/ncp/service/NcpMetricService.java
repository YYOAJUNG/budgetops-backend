package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.client.NcpApiClient;
import com.budgetops.backend.ncp.dto.NcpMetricDataPoint;
import com.budgetops.backend.ncp.dto.NcpMetricRequest;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NCP Cloud Insight 메트릭 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NcpMetricService {

    private static final String CLOUD_INSIGHT_QUERY_PATH = "/cw_fea/real/cw/api/data/query";
    private static final String SERVER_VPC_PRODUCT_NAME = "System/Server(VPC)";
    private static final String SERVER_VPC_CW_KEY = "460438474722512896";

    private final NcpApiClient apiClient;

    /**
     * 서버 인스턴스의 메트릭 데이터 조회
     *
     * @param account      NCP 계정 정보
     * @param instanceNo   서버 인스턴스 번호
     * @param metricName   메트릭 이름 (예: avg_cpu_used_rto, mem_usert)
     * @param durationMinutes 조회 기간 (분, 기본값: 5분)
     * @return 메트릭 평균값
     */
    public double getMetricValue(NcpAccount account, String instanceNo, String metricName, int durationMinutes) {
        try {
            List<NcpMetricDataPoint> dataPoints = queryMetricData(account, instanceNo, metricName, durationMinutes);

            if (dataPoints.isEmpty()) {
                log.warn("메트릭 데이터 없음: instanceNo={}, metric={}", instanceNo, metricName);
                return 0.0;
            }

            // 평균값 계산
            double sum = dataPoints.stream()
                    .mapToDouble(NcpMetricDataPoint::getValue)
                    .sum();

            return sum / dataPoints.size();

        } catch (Exception e) {
            log.error("메트릭 조회 실패: instanceNo={}, metric={}, error={}",
                    instanceNo, metricName, e.getMessage());
            return 0.0;
        }
    }

    /**
     * 서버 인스턴스의 메트릭 데이터 조회 (기본 5분)
     */
    public double getMetricValue(NcpAccount account, String instanceNo, String metricName) {
        return getMetricValue(account, instanceNo, metricName, 5);
    }

    /**
     * Cloud Insight API로 메트릭 데이터 조회
     *
     * @param account         NCP 계정 정보
     * @param instanceNo      서버 인스턴스 번호
     * @param metricName      메트릭 이름
     * @param durationMinutes 조회 기간 (분)
     * @return 메트릭 데이터 포인트 리스트
     */
    public List<NcpMetricDataPoint> queryMetricData(
            NcpAccount account,
            String instanceNo,
            String metricName,
            int durationMinutes
    ) {
        long now = Instant.now().toEpochMilli();
        long startTime = now - (durationMinutes * 60 * 1000L);

        // Dimension 설정
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("instanceNo", instanceNo);
        dimensions.put("type", "svr"); // Server 타입

        // Request 생성
        NcpMetricRequest request = NcpMetricRequest.builder()
                .cw_key(SERVER_VPC_CW_KEY)
                .productName(SERVER_VPC_PRODUCT_NAME)
                .metric(metricName)
                .timeStart(startTime)
                .timeEnd(now)
                .interval("Min1")
                .aggregation("AVG")
                .queryAggregation("AVG")
                .dimensions(dimensions)
                .build();

        // API 호출
        JsonNode response = apiClient.callCloudInsightApi(
                CLOUD_INSIGHT_QUERY_PATH,
                request,
                account.getAccessKey(),
                account.getSecretKeyEnc()
        );

        // Response 파싱
        return parseMetricResponse(response);
    }

    /**
     * Cloud Insight API 응답 파싱
     * Response 형식: [[timestamp1, value1], [timestamp2, value2], ...]
     */
    private List<NcpMetricDataPoint> parseMetricResponse(JsonNode response) {
        List<NcpMetricDataPoint> dataPoints = new ArrayList<>();

        if (response == null || !response.isArray()) {
            log.warn("잘못된 응답 형식: {}", response);
            return dataPoints;
        }

        for (JsonNode dataPoint : response) {
            if (dataPoint.isArray() && dataPoint.size() == 2) {
                Long timestamp = dataPoint.get(0).asLong();
                Double value = dataPoint.get(1).asDouble();
                dataPoints.add(new NcpMetricDataPoint(timestamp, value));
            }
        }

        return dataPoints;
    }
}
