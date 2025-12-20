package com.budgetops.backend.aws.controller;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.service.AwsAccountService;
import com.budgetops.backend.aws.service.AwsCostService;
import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.domain.user.entity.Member;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AwsAccountController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("AwsAccountController 테스트")
class AwsAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AwsAccountService accountService;

    @MockBean
    private AwsCostService costService;

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

    private AwsAccount testAccount;
    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");

        testAccount = new AwsAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test AWS Account");
        testAccount.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        testAccount.setSecretKeyLast4("EKEY");
        testAccount.setDefaultRegion("ap-northeast-2");
        testAccount.setActive(Boolean.TRUE);

        // SecurityContext 설정
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        given(securityContext.getAuthentication()).willReturn(authentication);
        given(authentication.getPrincipal()).willReturn(1L);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("AWS 계정 등록 성공")
    void register_Success() throws Exception {
        // given
        // Jackson의 WRITE_ONLY 설정으로 secretAccessKey가 직렬화되지 않으므로, 수동으로 JSON 본문을 구성합니다.
        String json = "{" +
                "\"name\":\"New AWS Account\"," +
                "\"accessKeyId\":\"AKIAIOSFODNN7NEWKEY\"," +
                "\"secretAccessKey\":\"newSecretAccessKeyExample123456789\"," +
                "\"defaultRegion\":\"us-east-1\"}";

        given(accountService.createWithVerify(any(AwsAccountCreateRequest.class), anyLong()))
                .willReturn(testAccount);

        // when & then
        mockMvc.perform(post("/api/aws/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.name").value("Test AWS Account"))
                .andExpect(jsonPath("$.secretKeyLast4").value("****EKEY"));
    }

    @Test
    @DisplayName("활성 계정 목록 조회 성공")
    void getActiveAccounts_Success() throws Exception {
        // given
        given(accountService.getActiveAccounts(anyLong()))
                .willReturn(List.of(testAccount));

        // when & then
        mockMvc.perform(get("/api/aws/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(100L));
    }

    @Test
    @DisplayName("계정 정보 조회 성공")
    void getAccountInfo_Success() throws Exception {
        // given
        given(accountService.getAccountInfo(anyLong(), anyLong()))
                .willReturn(testAccount);

        // when & then
        mockMvc.perform(get("/api/aws/accounts/100/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.name").value("Test AWS Account"));
    }

    @Test
    @DisplayName("계정 삭제 성공")
    void deleteAccount_Success() throws Exception {
        // given
        // when & then
        mockMvc.perform(delete("/api/aws/accounts/100"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("계정 비용 조회 성공")
    void getAccountCosts_Success() throws Exception {
        // given
        AwsCostService.DailyCost dailyCost = new AwsCostService.DailyCost(
                LocalDate.now().toString(),
                10.5,
                Collections.emptyList()
        );

        given(costService.getCosts(anyLong(), anyLong(), anyString(), anyString()))
                .willReturn(List.of(dailyCost));

        // when & then
        mockMvc.perform(get("/api/aws/accounts/100/costs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("월별 비용 조회 성공")
    void getAccountMonthlyCost_Success() throws Exception {
        // given
        AwsCostService.MonthlyCost monthlyCost = new AwsCostService.MonthlyCost(2024, 1, 100.0);

        given(costService.getMonthlyCost(anyLong(), anyLong(), anyInt(), anyInt()))
                .willReturn(monthlyCost);

        // when & then
        mockMvc.perform(get("/api/aws/accounts/100/costs/monthly")
                        .param("year", "2024")
                        .param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.month").value(1));
    }

    @Test
    @DisplayName("모든 계정 비용 조회 성공")
    void getAllAccountsCosts_Success() throws Exception {
        // given
        AwsCostService.AccountCost accountCost = new AwsCostService.AccountCost(100L, "Test Account", 200.0);

        given(costService.getAllAccountsCosts(anyLong(), anyString(), anyString()))
                .willReturn(List.of(accountCost));

        // when & then
        mockMvc.perform(get("/api/aws/accounts/costs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

