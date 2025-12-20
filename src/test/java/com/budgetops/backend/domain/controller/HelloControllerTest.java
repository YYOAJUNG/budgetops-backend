package com.budgetops.backend.domain.controller;

import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.oauth.filter.JwtAuthenticationFilter;
import com.budgetops.backend.oauth.handler.CustomLogoutSuccessHandler;
import com.budgetops.backend.oauth.handler.OAuth2AuthenticationFailureHandler;
import com.budgetops.backend.oauth.handler.OAuth2AuthenticationSuccessHandler;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HelloController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
        })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("HelloController 테스트")
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private AuditingHandler auditingHandler;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @MockBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AdminAuthUtil adminAuthUtil;

    @Test
    @DisplayName("루트 경로 요청")
    void root_Success() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("BudgetOps API Server is running"));
    }

    @Test
    @DisplayName("헬스 체크 요청")
    void health_Success() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("헬로우 API 요청")
    void hello_Success() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello from spring"));
    }
}

