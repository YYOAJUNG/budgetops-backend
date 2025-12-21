package com.budgetops.backend.ncp.controller;

import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.ncp.dto.NcpAccountCreateRequest;
import com.budgetops.backend.ncp.dto.NcpAccountResponse;
import com.budgetops.backend.ncp.dto.NcpCostSummary;
import com.budgetops.backend.ncp.dto.NcpMonthlyCost;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.service.NcpAccountService;
import com.budgetops.backend.ncp.service.NcpCostService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NcpAccountController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("NcpAccountController 테스트")
class NcpAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NcpAccountService accountService;

    @MockBean
    private NcpCostService costService;

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

    private NcpAccount testAccount;

    @BeforeEach
    void setUp() {
        // SecurityContext에 인증 정보 설정
        Authentication auth = new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        testAccount = new NcpAccount();
        testAccount.setId(1L);
        testAccount.setName("Test NCP Account");
        testAccount.setRegionCode("KR");
        testAccount.setAccessKey("NCPACCESSKEY123456");
        testAccount.setActive(true);
    }

    @Test
    @DisplayName("NCP 계정 등록")
    void register_Success() throws Exception {
        // given
        // Jackson의 WRITE_ONLY 설정으로 secretKey가 직렬화되지 않으므로, 수동으로 JSON 본문을 구성합니다.
        String json = "{" +
                "\"name\":\"Test NCP Account\"," +
                "\"accessKey\":\"NCPACCESSKEY123456\"," +
                "\"secretKey\":\"NCP_SECRET_KEY_12345678901234567890\"," +
                "\"regionCode\":\"KR\"}";

        given(accountService.createWithVerify(any(NcpAccountCreateRequest.class), anyLong()))
                .willReturn(testAccount);

        // when & then
        mockMvc.perform(post("/api/ncp/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test NCP Account"));
    }

    @Test
    @DisplayName("활성 계정 목록 조회")
    void getActiveAccounts_Success() throws Exception {
        // given
        given(accountService.getActiveAccounts(1L)).willReturn(List.of(testAccount));

        // when & then
        mockMvc.perform(get("/api/ncp/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    @DisplayName("계정 정보 조회")
    void getAccountInfo_Success() throws Exception {
        // given
        given(accountService.getAccountInfo(1L, 1L)).willReturn(testAccount);

        // when & then
        mockMvc.perform(get("/api/ncp/accounts/1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("계정 삭제")
    void deleteAccount_Success() throws Exception {
        // given
        org.mockito.Mockito.doNothing().when(accountService).deactivateAccount(1L, 1L);

        // when & then
        mockMvc.perform(delete("/api/ncp/accounts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("계정 비용 조회")
    void getAccountCosts_Success() throws Exception {
        // given
        given(costService.getCosts(anyLong(), anyLong(), anyString(), anyString()))
                .willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/api/ncp/accounts/1/costs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("계정 비용 요약 조회")
    void getAccountCostSummary_Success() throws Exception {
        // given
        NcpCostSummary summary = NcpCostSummary.builder()
                .month("202401")
                .totalCost(1000.0)
                .currency("KRW")
                .build();
        given(costService.getCostSummary(anyLong(), anyLong(), anyString())).willReturn(summary);

        // when & then
        mockMvc.perform(get("/api/ncp/accounts/1/costs/summary"))
                .andExpect(status().isOk());
    }
}

