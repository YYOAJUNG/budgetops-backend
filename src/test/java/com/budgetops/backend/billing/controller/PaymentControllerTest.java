package com.budgetops.backend.billing.controller;

import com.budgetops.backend.billing.dto.request.PaymentRegisterRequest;
import com.budgetops.backend.billing.dto.response.PaymentResponse;
import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.service.BillingService;
import com.budgetops.backend.billing.service.PaymentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@DisplayName("PaymentController 테스트")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

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
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .lastLoginAt(LocalDateTime.now())
                .build();

        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setMember(testMember);
        testPayment.setImpUid("imp_1234567890");
        testPayment.setCustomerUid("customer_1234567890");
    }

    @Test
    @DisplayName("결제 정보 등록")
    void registerPayment_Success() throws Exception {
        // given
        PaymentRegisterRequest request = new PaymentRegisterRequest();
        request.setImpUid("imp_1234567890");
        request.setCustomerUid("customer_1234567890");

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(paymentService.registerPayment(any(String.class), any(String.class), any(Member.class)))
                .willReturn(testPayment);

        // when & then
        mockMvc.perform(post("/api/v1/users/1/payment/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impUid").value("imp_1234567890"));
    }

    @Test
    @DisplayName("결제 등록 여부 확인")
    void checkPaymentStatus_Success() throws Exception {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(paymentService.isPaymentRegistered(any(Member.class))).willReturn(true);

        // when & then
        mockMvc.perform(get("/api/v1/users/1/payment/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    @DisplayName("결제 정보 삭제")
    void deletePayment_Success() throws Exception {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        org.mockito.Mockito.doNothing().when(paymentService).deletePayment(any(Member.class));

        // when & then
        mockMvc.perform(delete("/api/v1/users/1/payment"))
                .andExpect(status().isNoContent());
    }
}

