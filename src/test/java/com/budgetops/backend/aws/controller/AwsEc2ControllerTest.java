package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsEc2InstanceCreateRequest;
import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.dto.AwsEc2MetricsResponse;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.oauth.filter.JwtAuthenticationFilter;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AwsEc2Controller.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("AwsEc2Controller 테스트")
class AwsEc2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AwsEc2Service ec2Service;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private AdminAuthUtil adminAuthUtil;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private AuditingHandler auditingHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("EC2 인스턴스 목록 조회 성공")
    void listInstances_Success() throws Exception {
        // given
        AwsEc2InstanceResponse response = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .instanceType("t2.micro")
                .state("running")
                .build();

        given(ec2Service.listInstances(anyLong(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/aws/accounts/100/ec2/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].instanceId").value("i-1234567890abcdef0"));
    }

    @Test
    @DisplayName("EC2 인스턴스 상세 조회 성공")
    void getInstance_Success() throws Exception {
        // given
        AwsEc2InstanceResponse response = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .instanceType("t2.micro")
                .state("running")
                .build();

        given(ec2Service.getEc2Instance(anyLong(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/aws/accounts/100/ec2/instances/i-1234567890abcdef0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("i-1234567890abcdef0"));
    }

    @Test
    @DisplayName("EC2 인스턴스 메트릭 조회 성공")
    void getInstanceMetrics_Success() throws Exception {
        // given
        AwsEc2MetricsResponse.MetricDataPoint dataPoint = AwsEc2MetricsResponse.MetricDataPoint.builder()
                .timestamp("2024-01-01T00:00:00Z")
                .value(50.0)
                .unit("Percent")
                .build();

        AwsEc2MetricsResponse response = AwsEc2MetricsResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .cpuUtilization(List.of(dataPoint))
                .networkIn(List.of(dataPoint))
                .networkOut(List.of(dataPoint))
                .build();

        given(ec2Service.getInstanceMetrics(anyLong(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class), anyInt()))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/aws/accounts/100/ec2/instances/i-1234567890abcdef0/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("i-1234567890abcdef0"))
                .andExpect(jsonPath("$.cpuUtilization").isArray());
    }

    @Test
    @DisplayName("EC2 인스턴스 생성 성공")
    void createInstance_Success() throws Exception {
        // given
        AwsEc2InstanceCreateRequest request = new AwsEc2InstanceCreateRequest();
        request.setName("test-instance");
        request.setInstanceType("t2.micro");
        request.setImageId("ami-12345678");

        AwsEc2InstanceResponse response = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .instanceType("t2.micro")
                .state("pending")
                .build();

        given(ec2Service.createInstance(anyLong(), any(AwsEc2InstanceCreateRequest.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/aws/accounts/100/ec2/instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceId").value("i-1234567890abcdef0"));
    }

    @Test
    @DisplayName("EC2 인스턴스 중지 성공")
    void stopInstance_Success() throws Exception {
        // given
        AwsEc2InstanceResponse response = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .state("stopping")
                .build();

        given(ec2Service.stopInstance(anyLong(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/aws/accounts/100/ec2/instances/i-1234567890abcdef0/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("stopping"));
    }

    @Test
    @DisplayName("EC2 인스턴스 시작 성공")
    void startInstance_Success() throws Exception {
        // given
        AwsEc2InstanceResponse response = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .state("pending")
                .build();

        given(ec2Service.startInstance(anyLong(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/aws/accounts/100/ec2/instances/i-1234567890abcdef0/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("pending"));
    }

    @Test
    @DisplayName("EC2 인스턴스 종료 성공")
    void terminateInstance_Success() throws Exception {
        // given
        // when & then
        mockMvc.perform(delete("/api/aws/accounts/100/ec2/instances/i-1234567890abcdef0"))
                .andExpect(status().isNoContent());
    }
}

