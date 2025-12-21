package com.budgetops.backend.admin.controller;

import com.budgetops.backend.admin.controller.AdminController;
import com.budgetops.backend.admin.dto.AdminPaymentHistoryResponse;
import com.budgetops.backend.admin.dto.AdminTokenGrantRequest;
import com.budgetops.backend.admin.dto.UserListResponse;
import com.budgetops.backend.admin.service.AdminService;
import com.budgetops.backend.billing.constants.TokenConstants;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.budgetops.backend.billing.exception.GlobalExceptionHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController 테스트")
class AdminControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    private UsernamePasswordAuthenticationToken adminAuthentication;

    @BeforeEach
    void setUp() {
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setControllerAdvice(exceptionHandler)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        // 관리자 인증 설정
        adminAuthentication = new UsernamePasswordAuthenticationToken(
                1L, // principal은 memberId
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        // 기본적으로 관리자로 설정
        SecurityContextHolder.getContext().setAuthentication(adminAuthentication);
    }

    @Test
    @DisplayName("GET /api/admin/users - 사용자 목록 조회 성공")
    void getUserList_Success() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        UserListResponse user1 = UserListResponse.builder()
                .id(1L)
                .email("user1@example.com")
                .name("사용자1")
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .billingPlan("PRO")
                .currentTokens(5000)
                .cloudAccountCount(3)
                .awsAccountCount(1)
                .azureAccountCount(1)
                .gcpAccountCount(1)
                .ncpAccountCount(0)
                .build();

        UserListResponse user2 = UserListResponse.builder()
                .id(2L)
                .email("user2@example.com")
                .name("사용자2")
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .billingPlan("FREE")
                .currentTokens(1000)
                .cloudAccountCount(0)
                .awsAccountCount(0)
                .azureAccountCount(0)
                .gcpAccountCount(0)
                .ncpAccountCount(0)
                .build();

        Page<UserListResponse> userPage = new PageImpl<>(List.of(user1, user2), pageable, 2);

        given(adminService.getUserList(any(Pageable.class), isNull())).willReturn(userPage);

        // when & then
        mockMvc.perform(get("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].email").value("user1@example.com"))
                .andExpect(jsonPath("$.content[0].name").value("사용자1"))
                .andExpect(jsonPath("$.content[0].billingPlan").value("PRO"))
                .andExpect(jsonPath("$.content[0].currentTokens").value(5000))
                .andExpect(jsonPath("$.content[1].id").value(2L))
                .andExpect(jsonPath("$.content[1].billingPlan").value("FREE"));

        verify(adminService).getUserList(any(Pageable.class), isNull());
    }

    @Test
    @DisplayName("GET /api/admin/users - 검색 기능")
    void getUserList_Search() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        UserListResponse user = UserListResponse.builder()
                .id(1L)
                .email("user1@example.com")
                .name("사용자1")
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .billingPlan("PRO")
                .currentTokens(5000)
                .cloudAccountCount(0)
                .awsAccountCount(0)
                .azureAccountCount(0)
                .gcpAccountCount(0)
                .ncpAccountCount(0)
                .build();

        Page<UserListResponse> userPage = new PageImpl<>(List.of(user), pageable, 1);

        given(adminService.getUserList(any(Pageable.class), eq("사용자1"))).willReturn(userPage);

        // when & then
        mockMvc.perform(get("/api/admin/users")
                        .param("search", "사용자1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("사용자1"));

        verify(adminService).getUserList(any(Pageable.class), eq("사용자1"));
    }

    @Test
    @DisplayName("GET /api/admin/users - 페이지네이션")
    void getUserList_Pagination() throws Exception {
        // given
        Pageable pageable = PageRequest.of(1, 1);
        UserListResponse user = UserListResponse.builder()
                .id(2L)
                .email("user2@example.com")
                .name("사용자2")
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .billingPlan("FREE")
                .currentTokens(1000)
                .cloudAccountCount(0)
                .awsAccountCount(0)
                .azureAccountCount(0)
                .gcpAccountCount(0)
                .ncpAccountCount(0)
                .build();

        Page<UserListResponse> userPage = new PageImpl<>(List.of(user), pageable, 2);

        given(adminService.getUserList(any(Pageable.class), isNull())).willReturn(userPage);

        // when & then
        mockMvc.perform(get("/api/admin/users")
                        .param("page", "1")
                        .param("size", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/admin/users - 빈 결과")
    void getUserList_Empty() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserListResponse> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        given(adminService.getUserList(any(Pageable.class), isNull())).willReturn(emptyPage);

        // when & then
        mockMvc.perform(get("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/admin/payments - 결제 내역 조회 성공")
    void getAllPaymentHistory_Success() throws Exception {
        // given
        AdminPaymentHistoryResponse payment1 = AdminPaymentHistoryResponse.builder()
                .id(1L)
                .userId(1L)
                .userEmail("user1@example.com")
                .userName("사용자1")
                .paymentType("MEMBERSHIP")
                .impUid("imp_1234567890")
                .amount(null)
                .status("PAID")
                .createdAt(LocalDateTime.now().minusDays(2))
                .lastVerifiedAt(LocalDateTime.now().minusDays(2))
                .build();

        AdminPaymentHistoryResponse payment2 = AdminPaymentHistoryResponse.builder()
                .id(2L)
                .userId(2L)
                .userEmail("user2@example.com")
                .userName("사용자2")
                .paymentType("TOKEN_PURCHASE")
                .impUid("imp_9876543210")
                .amount(10000)
                .status("PAID")
                .createdAt(LocalDateTime.now().minusDays(1))
                .lastVerifiedAt(LocalDateTime.now().minusDays(1))
                .build();

        List<AdminPaymentHistoryResponse> payments = List.of(payment1, payment2);

        given(adminService.getAllPaymentHistory(isNull())).willReturn(payments);

        // when & then
        mockMvc.perform(get("/api/admin/payments")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].paymentType").value("MEMBERSHIP"))
                .andExpect(jsonPath("$[1].paymentType").value("TOKEN_PURCHASE"));

        verify(adminService).getAllPaymentHistory(isNull());
    }

    @Test
    @DisplayName("GET /api/admin/payments - 검색 기능")
    void getAllPaymentHistory_Search() throws Exception {
        // given
        AdminPaymentHistoryResponse payment = AdminPaymentHistoryResponse.builder()
                .id(1L)
                .userId(1L)
                .userEmail("user1@example.com")
                .userName("사용자1")
                .paymentType("MEMBERSHIP")
                .impUid("imp_1234567890")
                .amount(null)
                .status("PAID")
                .createdAt(LocalDateTime.now())
                .lastVerifiedAt(LocalDateTime.now())
                .build();

        List<AdminPaymentHistoryResponse> payments = List.of(payment);

        given(adminService.getAllPaymentHistory(eq("사용자1"))).willReturn(payments);

        // when & then
        mockMvc.perform(get("/api/admin/payments")
                        .param("search", "사용자1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userName").value("사용자1"));

        verify(adminService).getAllPaymentHistory(eq("사용자1"));
    }

    @Test
    @DisplayName("GET /api/admin/payments - 빈 결과")
    void getAllPaymentHistory_Empty() throws Exception {
        // given
        given(adminService.getAllPaymentHistory(isNull())).willReturn(Collections.emptyList());

        // when & then
        mockMvc.perform(get("/api/admin/payments")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/tokens - 토큰 부여 성공")
    void grantTokens_Success() throws Exception {
        // given
        Long userId = 1L;
        int tokensToGrant = 1000;
        int newTotalTokens = 6000;

        AdminTokenGrantRequest request = new AdminTokenGrantRequest(tokensToGrant, "테스트 토큰 부여");

        given(adminService.grantTokens(userId, tokensToGrant, "테스트 토큰 부여"))
                .willReturn(newTotalTokens);

        // when & then
        mockMvc.perform(post("/api/admin/users/{userId}/tokens", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(newTotalTokens));

        verify(adminService).grantTokens(userId, tokensToGrant, "테스트 토큰 부여");
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/tokens - 사용자 없음 (404)")
    void grantTokens_UserNotFound() throws Exception {
        // given
        Long userId = 999L;
        AdminTokenGrantRequest request = new AdminTokenGrantRequest(1000, "테스트");

        doThrow(new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId))
                .when(adminService).grantTokens(userId, 1000, "테스트");

        // when & then
        mockMvc.perform(post("/api/admin/users/{userId}/tokens", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다: 999"));
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/tokens - 토큰 한도 초과 (400)")
    void grantTokens_ExceedsLimit() throws Exception {
        // given
        Long userId = 1L;
        int tokensToGrant = TokenConstants.MAX_TOKEN_LIMIT + 1;
        AdminTokenGrantRequest request = new AdminTokenGrantRequest(tokensToGrant, "테스트");

        doThrow(new IllegalStateException("토큰 보유량 한도를 초과할 수 없습니다"))
                .when(adminService).grantTokens(userId, tokensToGrant, "테스트");

        // when & then
        mockMvc.perform(post("/api/admin/users/{userId}/tokens", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("토큰 보유량 한도를 초과할 수 없습니다"));
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/tokens - 유효성 검증 실패 (null 토큰)")
    void grantTokens_ValidationNull() throws Exception {
        // given
        Long userId = 1L;
        AdminTokenGrantRequest request = new AdminTokenGrantRequest(null, "테스트");

        // when & then
        mockMvc.perform(post("/api/admin/users/{userId}/tokens", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/tokens - 유효성 검증 실패 (음수 토큰)")
    void grantTokens_ValidationNegative() throws Exception {
        // given
        Long userId = 1L;
        AdminTokenGrantRequest request = new AdminTokenGrantRequest(-1, "테스트");

        // when & then
        mockMvc.perform(post("/api/admin/users/{userId}/tokens", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/tokens - 유효성 검증 실패 (0 토큰)")
    void grantTokens_ValidationZero() throws Exception {
        // given
        Long userId = 1L;
        AdminTokenGrantRequest request = new AdminTokenGrantRequest(0, "테스트");

        // when & then
        mockMvc.perform(post("/api/admin/users/{userId}/tokens", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/users/{userId}/tokens - Billing 없음")
    void grantTokens_BillingNotFound() throws Exception {
        // given
        Long userId = 1L;
        AdminTokenGrantRequest request = new AdminTokenGrantRequest(1000, "테스트");

        doThrow(new BillingNotFoundException(userId))
                .when(adminService).grantTokens(userId, 1000, "테스트");

        // when & then
        mockMvc.perform(post("/api/admin/users/{userId}/tokens", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 요청 - SecurityContext가 비어있음")
    void unauthenticated_Request() {
        // given
        SecurityContextHolder.clearContext();

        // when & then
        try {
            adminController.getUserList(PageRequest.of(0, 20), null);
            // 예외가 발생하지 않으면 실패
            throw new AssertionError("ClassCastException 또는 NullPointerException이 발생해야 합니다");
        } catch (ClassCastException | NullPointerException e) {
            // 예상된 동작 - SecurityContext가 비어있으면 getPrincipal()이 null이거나 다른 타입
        }
    }
}
