package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.client.NcpApiClient;
import com.budgetops.backend.ncp.dto.NcpMetricDataPoint;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("NcpMetric Service 테스트")
class NcpMetricServiceTest {

    @Mock
    private NcpApiClient apiClient;

    @InjectMocks
    private NcpMetricService ncpMetricService;

    private NcpAccount testAccount;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        testAccount = new NcpAccount();
        testAccount.setId(100L);
        testAccount.setAccessKey("NCPACCESSKEY123456");
        testAccount.setSecretKeyEnc("secret");
        testAccount.setActive(Boolean.TRUE);
    }

    @Test
    @DisplayName("getMetricValue - 메트릭 데이터 조회 성공")
    void getMetricValue_Success() throws Exception {
        // given
        String jsonResponse = "[[1000000000, 50.5], [1000000060, 55.0], [1000000120, 60.0]]";
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        given(apiClient.callCloudInsightApi(anyString(), any(), anyString(), anyString()))
                .willReturn(responseNode);

        // when
        double result = ncpMetricService.getMetricValue(testAccount, "instance-123", "avg_cpu_used_rto", 5);

        // then
        assertThat(result).isGreaterThan(0);
        // 평균값: (50.5 + 55.0 + 60.0) / 3 = 55.17
        assertThat(result).isCloseTo(55.17, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    @DisplayName("getMetricValue - 메트릭 데이터 없음")
    void getMetricValue_NoData() throws Exception {
        // given
        String jsonResponse = "[]";
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        given(apiClient.callCloudInsightApi(anyString(), any(), anyString(), anyString()))
                .willReturn(responseNode);

        // when
        double result = ncpMetricService.getMetricValue(testAccount, "instance-123", "avg_cpu_used_rto", 5);

        // then
        assertThat(result).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getMetricValue - 기본 durationMinutes 사용")
    void getMetricValue_DefaultDuration() throws Exception {
        // given
        String jsonResponse = "[[1000000000, 50.0]]";
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        given(apiClient.callCloudInsightApi(anyString(), any(), anyString(), anyString()))
                .willReturn(responseNode);

        // when
        double result = ncpMetricService.getMetricValue(testAccount, "instance-123", "avg_cpu_used_rto");

        // then
        assertThat(result).isEqualTo(50.0);
    }

    @Test
    @DisplayName("queryMetricData - 메트릭 데이터 포인트 리스트 반환")
    void queryMetricData_Success() throws Exception {
        // given
        String jsonResponse = "[[1000000000, 50.5], [1000000060, 55.0]]";
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        given(apiClient.callCloudInsightApi(anyString(), any(), anyString(), anyString()))
                .willReturn(responseNode);

        // when
        List<NcpMetricDataPoint> result = ncpMetricService.queryMetricData(
                testAccount, "instance-123", "avg_cpu_used_rto", 5);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getValue()).isEqualTo(50.5);
        assertThat(result.get(1).getValue()).isEqualTo(55.0);
    }

    @Test
    @DisplayName("queryMetricData - 잘못된 응답 형식")
    void queryMetricData_InvalidResponse() throws Exception {
        // given
        String jsonResponse = "{}";
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        given(apiClient.callCloudInsightApi(anyString(), any(), anyString(), anyString()))
                .willReturn(responseNode);

        // when
        List<NcpMetricDataPoint> result = ncpMetricService.queryMetricData(
                testAccount, "instance-123", "avg_cpu_used_rto", 5);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMetricValue - API 호출 실패 시 0 반환")
    void getMetricValue_ApiFailure() {
        // given
        given(apiClient.callCloudInsightApi(anyString(), any(), anyString(), anyString()))
                .willThrow(new RuntimeException("API 오류"));

        // when
        double result = ncpMetricService.getMetricValue(testAccount, "instance-123", "avg_cpu_used_rto", 5);

        // then
        assertThat(result).isEqualTo(0.0);
    }
}

