package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.gcp.dto.GcpAccountResponse;
import com.budgetops.backend.gcp.dto.SaveIntegrationRequest;
import com.budgetops.backend.gcp.dto.SaveIntegrationResponse;
import com.budgetops.backend.gcp.dto.TestIntegrationRequest;
import com.budgetops.backend.gcp.dto.TestIntegrationResponse;
import com.budgetops.backend.gcp.service.GcpAccountService;
import com.budgetops.backend.oauth.filter.JwtAuthenticationFilter;
import com.budgetops.backend.oauth.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GcpAccountController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("GcpAccountController 테스트")
class GcpAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GcpAccountService accountService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private AdminAuthUtil adminAuthUtil;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("GCP 계정 목록 조회")
    void listAccounts_Success() throws Exception {
        // given
        GcpAccountResponse account = new GcpAccountResponse();
        account.setId(1L);
        account.setName("Test GCP Account");
        given(accountService.listAccounts(anyLong())).willReturn(List.of(account));

        // when & then
        mockMvc.perform(get("/api/gcp/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Test GCP Account"));
    }

    @Test
    @DisplayName("GCP 통합 테스트")
    void testIntegration_Success() throws Exception {
        // given
        TestIntegrationRequest request = new TestIntegrationRequest();
        request.setServiceAccountId("test@project.iam.gserviceaccount.com");
        request.setServiceAccountKeyJson("{\"type\":\"service_account\"}");

        TestIntegrationResponse response = new TestIntegrationResponse();
        response.setOk(true);
        response.setMessage("테스트 성공");

        given(accountService.testIntegration(any(TestIntegrationRequest.class)))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/gcp/accounts/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @DisplayName("GCP 통합 저장")
    void saveIntegration_Success() throws Exception {
        // given
        SaveIntegrationRequest request = new SaveIntegrationRequest();
        request.setName("Test Account");
        request.setServiceAccountId("test@project.iam.gserviceaccount.com");
        request.setServiceAccountKeyJson("{\"type\":\"service_account\"}");

        SaveIntegrationResponse response = new SaveIntegrationResponse();
        response.setId(1L);
        response.setOk(true);

        given(accountService.saveIntegration(any(SaveIntegrationRequest.class), anyLong()))
                .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/gcp/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("GCP 계정 삭제")
    void deleteAccount_Success() throws Exception {
        // given
        // deleteAccount는 void 메서드이므로 doNothing 사용
        org.mockito.Mockito.doNothing().when(accountService).deleteAccount(1L, 1L);

        // when & then
        mockMvc.perform(delete("/api/gcp/accounts/1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GCP 계정 삭제 - 계정을 찾을 수 없음")
    void deleteAccount_NotFound() throws Exception {
        // given
        org.mockito.Mockito.doThrow(new IllegalArgumentException("GCP 계정을 찾을 수 없습니다: 999"))
                .when(accountService).deleteAccount(999L, 1L);

        // when & then
        mockMvc.perform(delete("/api/gcp/accounts/999"))
                .andExpect(status().isNotFound());
    }
}
