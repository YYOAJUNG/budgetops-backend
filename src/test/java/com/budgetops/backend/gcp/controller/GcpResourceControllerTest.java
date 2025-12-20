package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.dto.GcpResourceMetricsResponse;
import com.budgetops.backend.gcp.service.GcpResourceService;
import com.budgetops.backend.oauth.filter.JwtAuthenticationFilter;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GcpResourceController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("GcpResourceController 테스트")
class GcpResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GcpResourceService resourceService;

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

    @BeforeEach
    void setUp() {
        // SecurityContext에 인증 정보 설정
        Authentication auth = new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("특정 계정의 리소스 목록 조회")
    void listResources_Success() throws Exception {
        // given
        GcpResourceListResponse response = new GcpResourceListResponse();
        response.setAccountId(1L);
        given(resourceService.listResources(1L, 1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/gcp/accounts/1/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1L));
    }

    @Test
    @DisplayName("모든 계정의 리소스 목록 조회")
    void listAllAccountsResources_Success() throws Exception {
        // given
        GcpResourceListResponse response = new GcpResourceListResponse();
        response.setAccountId(1L);
        given(resourceService.listAllAccountsResources(1L))
                .willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/gcp/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(1L));
    }

    @Test
    @DisplayName("리소스 메트릭 조회")
    void getResourceMetrics_Success() throws Exception {
        // given
        GcpResourceMetricsResponse metrics = GcpResourceMetricsResponse.builder()
                .resourceId("test-resource-id")
                .resourceType("compute.googleapis.com/Instance")
                .region("us-central1")
                .cpuUtilization(Collections.emptyList())
                .networkIn(Collections.emptyList())
                .networkOut(Collections.emptyList())
                .memoryUtilization(Collections.emptyList())
                .build();
        given(resourceService.getResourceMetrics(anyString(), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(metrics);

        // when & then
        mockMvc.perform(get("/api/gcp/resources/test-resource-id/metrics")
                        .param("hours", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("test-resource-id"));
    }

    @Test
    @DisplayName("리소스 메트릭 조회 - 기본 hours 파라미터")
    void getResourceMetrics_DefaultHours() throws Exception {
        // given
        GcpResourceMetricsResponse metrics = GcpResourceMetricsResponse.builder()
                .resourceId("test-resource-id")
                .resourceType("compute.googleapis.com/Instance")
                .region("us-central1")
                .cpuUtilization(Collections.emptyList())
                .networkIn(Collections.emptyList())
                .networkOut(Collections.emptyList())
                .memoryUtilization(Collections.emptyList())
                .build();
        given(resourceService.getResourceMetrics(anyString(), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(metrics);

        // when & then
        mockMvc.perform(get("/api/gcp/resources/test-resource-id/metrics"))
                .andExpect(status().isOk());
    }
}

