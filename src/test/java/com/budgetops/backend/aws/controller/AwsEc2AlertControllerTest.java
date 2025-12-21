package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import com.budgetops.backend.aws.service.AwsAlertService;
import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.oauth.filter.JwtAuthenticationFilter;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AwsEc2AlertController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("AwsEc2AlertController 테스트")
class AwsEc2AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AwsAlertService alertService;

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

    @Test
    @DisplayName("모든 계정 알림 확인")
    void checkAllAccounts_Success() throws Exception {
        // given
        given(alertService.checkAllServices()).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(post("/api/aws/alerts/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("특정 계정 알림 확인")
    void checkAccount_Success() throws Exception {
        // given
        given(alertService.checkAllServicesForAccount(1L)).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(post("/api/aws/alerts/check/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

