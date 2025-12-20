package com.budgetops.backend.billing.controller;

import com.budgetops.backend.billing.dto.response.BillingResponse;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.enums.BillingPlan;
import com.budgetops.backend.billing.exception.MemberNotFoundException;
import com.budgetops.backend.billing.service.BillingService;
import com.budgetops.backend.config.AdminAuthUtil;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("BillingController 테스트")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BillingService billingService;

    @MockBean
    private MemberRepository memberRepository;

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

    private Member testMember;
    private Billing testBilling;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .lastLoginAt(LocalDateTime.now())
                .build();

        testBilling = Billing.builder()
                .id(1L)
                .member(testMember)
                .currentPlan(BillingPlan.FREE)
                .currentPrice(0)
                .currentTokens(10000)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("요금제 정보 조회 성공")
    void getBilling_Success() throws Exception {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(billingService.getBillingByMember(any(Member.class)))
                .willReturn(Optional.of(testBilling));

        // when & then
        mockMvc.perform(get("/api/v1/users/1/billing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlan").value("FREE"))
                .andExpect(jsonPath("$.currentPrice").value(0))
                .andExpect(jsonPath("$.currentTokens").value(10000));
    }

    @Test
    @DisplayName("요금제 정보 조회 - Billing이 없으면 자동 초기화")
    void getBilling_AutoInitialize() throws Exception {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(billingService.getBillingByMember(any(Member.class)))
                .willReturn(Optional.empty());
        given(billingService.initializeBilling(any(Member.class)))
                .willReturn(testBilling);

        // when & then
        mockMvc.perform(get("/api/v1/users/1/billing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlan").value("FREE"));
    }

    @Test
    @DisplayName("요금제 정보 조회 - 사용자를 찾을 수 없음")
    void getBilling_MemberNotFound() throws Exception {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/v1/users/999/billing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("요금제 변경 성공")
    void changePlan_Success() throws Exception {
        // given
        Billing proBilling = Billing.builder()
                .id(1L)
                .member(testMember)
                .currentPlan(BillingPlan.PRO)
                .currentPrice(9900)
                .currentTokens(50000)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(billingService.getBillingByMember(any(Member.class)))
                .willReturn(Optional.of(testBilling));
        given(billingService.changePlan(any(Member.class), any(String.class)))
                .willReturn(proBilling);

        // when & then
        mockMvc.perform(put("/api/v1/users/1/billing/plan/PRO")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlan").value("PRO"))
                .andExpect(jsonPath("$.currentPrice").value(9900));
    }

    @Test
    @DisplayName("요금제 변경 - Billing이 없으면 먼저 초기화")
    void changePlan_AutoInitialize() throws Exception {
        // given
        Billing proBilling = Billing.builder()
                .id(1L)
                .member(testMember)
                .currentPlan(BillingPlan.PRO)
                .currentPrice(9900)
                .currentTokens(50000)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(billingService.getBillingByMember(any(Member.class)))
                .willReturn(Optional.empty());
        given(billingService.initializeBilling(any(Member.class)))
                .willReturn(testBilling);
        given(billingService.changePlan(any(Member.class), any(String.class)))
                .willReturn(proBilling);

        // when & then
        mockMvc.perform(put("/api/v1/users/1/billing/plan/PRO")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlan").value("PRO"));
    }
}

