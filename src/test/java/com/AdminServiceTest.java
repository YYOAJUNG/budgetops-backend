package com;

import com.budgetops.backend.admin.dto.AdminPaymentHistoryResponse;
import com.budgetops.backend.admin.dto.UserListResponse;
import com.budgetops.backend.admin.service.AdminService;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.billing.constants.TokenConstants;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.entity.PaymentHistory;
import com.budgetops.backend.billing.enums.BillingPlan;
import com.budgetops.backend.billing.enums.PaymentStatus;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.repository.BillingRepository;
import com.budgetops.backend.billing.repository.PaymentHistoryRepository;
import com.budgetops.backend.billing.repository.PaymentRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService 테스트")
class AdminServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BillingRepository billingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private AwsAccountRepository awsAccountRepository;

    @Mock
    private AzureAccountRepository azureAccountRepository;

    @Mock
    private GcpAccountRepository gcpAccountRepository;

    @Mock
    private NcpAccountRepository ncpAccountRepository;

    @InjectMocks
    private AdminService adminService;

    private Member testMember1;
    private Member testMember2;
    private Billing testBilling1;
    private Billing testBilling2;
    private Payment testPayment;
    private PaymentHistory testPaymentHistory1;
    private PaymentHistory testPaymentHistory2;

    @BeforeEach
    void setUp() {
        // 테스트 멤버 1
        testMember1 = Member.builder()
                .id(1L)
                .email("user1@example.com")
                .name("사용자1")
                .createdAt(LocalDateTime.now().minusDays(10))
                .lastLoginAt(LocalDateTime.now().minusDays(1))
                .build();

        // 테스트 멤버 2
        testMember2 = Member.builder()
                .id(2L)
                .email("user2@example.com")
                .name("사용자2")
                .createdAt(LocalDateTime.now().minusDays(5))
                .lastLoginAt(LocalDateTime.now().minusHours(5))
                .build();

        // 테스트 Billing 1
        testBilling1 = Billing.builder()
                .id(1L)
                .member(testMember1)
                .currentPlan(BillingPlan.PRO)
                .currentPrice(4900)
                .currentTokens(5000)
                .build();

        // 테스트 Billing 2
        testBilling2 = Billing.builder()
                .id(2L)
                .member(testMember2)
                .currentPlan(BillingPlan.FREE)
                .currentPrice(0)
                .currentTokens(1000)
                .build();

        // 테스트 Payment
        testPayment = Payment.builder()
                .id(1L)
                .member(testMember1)
                .impUid("imp_1234567890")
                .status(PaymentStatus.PAID)
                .createdAt(LocalDateTime.now().minusDays(2))
                .lastVerifiedAt(LocalDateTime.now().minusDays(2))
                .build();

        // 테스트 PaymentHistory 1 (멤버십)
        testPaymentHistory1 = PaymentHistory.builder()
                .id(1L)
                .member(testMember1)
                .impUid("imp_9876543210")
                .merchantUid("merchant_001")
                .amount(4900)
                .orderName("Pro 플랜 결제")
                .status(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now().minusDays(3))
                .createdAt(LocalDateTime.now().minusDays(3))
                .build();

        // 테스트 PaymentHistory 2 (토큰 구매)
        testPaymentHistory2 = PaymentHistory.builder()
                .id(2L)
                .member(testMember2)
                .impUid("imp_1111111111")
                .merchantUid("merchant_002")
                .amount(10000)
                .orderName("토큰 10,000개 구매")
                .status(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Test
    @DisplayName("getUserList - 전체 조회 성공")
    void getUserList_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        List<Member> members = List.of(testMember1, testMember2);
        Page<Member> memberPage = new PageImpl<>(members, pageable, 2);

        given(memberRepository.findAllOrderByLastLoginAtDesc(pageable)).willReturn(memberPage);
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.of(testBilling1));
        given(billingRepository.findByMember(testMember2)).willReturn(Optional.of(testBilling2));
        given(awsAccountRepository.countByOwnerId(1L)).willReturn(2L);
        given(azureAccountRepository.countByOwnerId(1L)).willReturn(1L);
        given(gcpAccountRepository.countByOwnerId(1L)).willReturn(0L);
        given(ncpAccountRepository.countByOwnerId(1L)).willReturn(1L);
        given(awsAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(azureAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(gcpAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(ncpAccountRepository.countByOwnerId(2L)).willReturn(0L);

        // when
        Page<UserListResponse> result = adminService.getUserList(pageable, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);

        UserListResponse user1 = result.getContent().get(0);
        assertThat(user1.getId()).isEqualTo(1L);
        assertThat(user1.getEmail()).isEqualTo("user1@example.com");
        assertThat(user1.getName()).isEqualTo("사용자1");
        assertThat(user1.getBillingPlan()).isEqualTo("PRO");
        assertThat(user1.getCurrentTokens()).isEqualTo(5000);
        assertThat(user1.getCloudAccountCount()).isEqualTo(4);
        assertThat(user1.getAwsAccountCount()).isEqualTo(2);
        assertThat(user1.getAzureAccountCount()).isEqualTo(1);
        assertThat(user1.getGcpAccountCount()).isEqualTo(0);
        assertThat(user1.getNcpAccountCount()).isEqualTo(1);

        UserListResponse user2 = result.getContent().get(1);
        assertThat(user2.getId()).isEqualTo(2L);
        assertThat(user2.getBillingPlan()).isEqualTo("FREE");
        assertThat(user2.getCurrentTokens()).isEqualTo(1000);
        assertThat(user2.getCloudAccountCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("getUserList - 검색 기능 (이름)")
    void getUserList_SearchByName() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        List<Member> members = List.of(testMember1);
        Page<Member> memberPage = new PageImpl<>(members, pageable, 1);

        given(memberRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByLastLoginAtDesc(
                "사용자1", pageable)).willReturn(memberPage);
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.of(testBilling1));
        given(awsAccountRepository.countByOwnerId(1L)).willReturn(0L);
        given(azureAccountRepository.countByOwnerId(1L)).willReturn(0L);
        given(gcpAccountRepository.countByOwnerId(1L)).willReturn(0L);
        given(ncpAccountRepository.countByOwnerId(1L)).willReturn(0L);

        // when
        Page<UserListResponse> result = adminService.getUserList(pageable, "사용자1");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("사용자1");
    }

    @Test
    @DisplayName("getUserList - 검색 기능 (이메일)")
    void getUserList_SearchByEmail() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        List<Member> members = List.of(testMember2);
        Page<Member> memberPage = new PageImpl<>(members, pageable, 1);

        given(memberRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByLastLoginAtDesc(
                "user2@example.com", pageable)).willReturn(memberPage);
        given(billingRepository.findByMember(testMember2)).willReturn(Optional.of(testBilling2));
        given(awsAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(azureAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(gcpAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(ncpAccountRepository.countByOwnerId(2L)).willReturn(0L);

        // when
        Page<UserListResponse> result = adminService.getUserList(pageable, "user2@example.com");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("user2@example.com");
    }

    @Test
    @DisplayName("getUserList - 페이지네이션")
    void getUserList_Pagination() {
        // given
        Pageable pageable = PageRequest.of(1, 1); // 두 번째 페이지, 크기 1
        List<Member> members = List.of(testMember2);
        Page<Member> memberPage = new PageImpl<>(members, pageable, 2);

        given(memberRepository.findAllOrderByLastLoginAtDesc(pageable)).willReturn(memberPage);
        given(billingRepository.findByMember(testMember2)).willReturn(Optional.of(testBilling2));
        given(awsAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(azureAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(gcpAccountRepository.countByOwnerId(2L)).willReturn(0L);
        given(ncpAccountRepository.countByOwnerId(2L)).willReturn(0L);

        // when
        Page<UserListResponse> result = adminService.getUserList(pageable, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("getUserList - Billing 없는 사용자 처리")
    void getUserList_NoBilling() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        List<Member> members = List.of(testMember1);
        Page<Member> memberPage = new PageImpl<>(members, pageable, 1);

        given(memberRepository.findAllOrderByLastLoginAtDesc(pageable)).willReturn(memberPage);
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.empty());
        given(awsAccountRepository.countByOwnerId(1L)).willReturn(0L);
        given(azureAccountRepository.countByOwnerId(1L)).willReturn(0L);
        given(gcpAccountRepository.countByOwnerId(1L)).willReturn(0L);
        given(ncpAccountRepository.countByOwnerId(1L)).willReturn(0L);

        // when
        Page<UserListResponse> result = adminService.getUserList(pageable, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        UserListResponse user = result.getContent().get(0);
        assertThat(user.getBillingPlan()).isEqualTo("FREE");
        assertThat(user.getCurrentTokens()).isEqualTo(0);
    }

    @Test
    @DisplayName("getUserList - 클라우드 계정 개수 집계")
    void getUserList_CloudAccountCount() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        List<Member> members = List.of(testMember1);
        Page<Member> memberPage = new PageImpl<>(members, pageable, 1);

        given(memberRepository.findAllOrderByLastLoginAtDesc(pageable)).willReturn(memberPage);
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.of(testBilling1));
        given(awsAccountRepository.countByOwnerId(1L)).willReturn(3L);
        given(azureAccountRepository.countByOwnerId(1L)).willReturn(2L);
        given(gcpAccountRepository.countByOwnerId(1L)).willReturn(1L);
        given(ncpAccountRepository.countByOwnerId(1L)).willReturn(4L);

        // when
        Page<UserListResponse> result = adminService.getUserList(pageable, null);

        // then
        assertThat(result).isNotNull();
        UserListResponse user = result.getContent().get(0);
        assertThat(user.getCloudAccountCount()).isEqualTo(10);
        assertThat(user.getAwsAccountCount()).isEqualTo(3);
        assertThat(user.getAzureAccountCount()).isEqualTo(2);
        assertThat(user.getGcpAccountCount()).isEqualTo(1);
        assertThat(user.getNcpAccountCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("getUserList - 빈 결과")
    void getUserList_Empty() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        Page<Member> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        given(memberRepository.findAllOrderByLastLoginAtDesc(pageable)).willReturn(emptyPage);

        // when
        Page<UserListResponse> result = adminService.getUserList(pageable, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("getAllPaymentHistory - 전체 조회 성공")
    void getAllPaymentHistory_Success() {
        // given
        List<Payment> payments = List.of(testPayment);
        List<PaymentHistory> paymentHistories = List.of(testPaymentHistory1, testPaymentHistory2);

        given(paymentRepository.findAll()).willReturn(payments);
        given(paymentHistoryRepository.findAll()).willReturn(paymentHistories);

        // when
        List<AdminPaymentHistoryResponse> result = adminService.getAllPaymentHistory(null);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3); // Payment 1개 + PaymentHistory 2개
    }

    @Test
    @DisplayName("getAllPaymentHistory - Payment와 PaymentHistory 통합")
    void getAllPaymentHistory_Integration() {
        // given
        List<Payment> payments = List.of(testPayment);
        List<PaymentHistory> paymentHistories = List.of(testPaymentHistory1);

        given(paymentRepository.findAll()).willReturn(payments);
        given(paymentHistoryRepository.findAll()).willReturn(paymentHistories);

        // when
        List<AdminPaymentHistoryResponse> result = adminService.getAllPaymentHistory(null);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);

        // Payment가 MEMBERSHIP으로 변환되었는지 확인
        AdminPaymentHistoryResponse paymentResponse = result.stream()
                .filter(r -> r.getImpUid().equals("imp_1234567890"))
                .findFirst()
                .orElseThrow();
        assertThat(paymentResponse.getPaymentType()).isEqualTo("MEMBERSHIP");
        assertThat(paymentResponse.getUserId()).isEqualTo(1L);
        assertThat(paymentResponse.getUserEmail()).isEqualTo("user1@example.com");

        // PaymentHistory가 MEMBERSHIP으로 변환되었는지 확인
        AdminPaymentHistoryResponse historyResponse = result.stream()
                .filter(r -> r.getImpUid().equals("imp_9876543210"))
                .findFirst()
                .orElseThrow();
        assertThat(historyResponse.getPaymentType()).isEqualTo("MEMBERSHIP");
    }

    @Test
    @DisplayName("getAllPaymentHistory - 검색 기능")
    void getAllPaymentHistory_Search() {
        // given
        List<Payment> payments = List.of(testPayment);
        List<PaymentHistory> paymentHistories = List.of(testPaymentHistory1);

        given(paymentRepository.findByMemberNameOrEmailContaining("사용자1")).willReturn(payments);
        given(paymentHistoryRepository.findByMemberNameOrEmailContaining("사용자1")).willReturn(paymentHistories);

        // when
        List<AdminPaymentHistoryResponse> result = adminService.getAllPaymentHistory("사용자1");

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getUserName().equals("사용자1") || r.getUserEmail().equals("user1@example.com"));
    }

    @Test
    @DisplayName("getAllPaymentHistory - PaymentType 분류 (MEMBERSHIP vs TOKEN_PURCHASE)")
    void getAllPaymentHistory_PaymentTypeClassification() {
        // given
        List<PaymentHistory> paymentHistories = List.of(testPaymentHistory1, testPaymentHistory2);

        given(paymentRepository.findAll()).willReturn(Collections.emptyList());
        given(paymentHistoryRepository.findAll()).willReturn(paymentHistories);

        // when
        List<AdminPaymentHistoryResponse> result = adminService.getAllPaymentHistory(null);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);

        AdminPaymentHistoryResponse membership = result.stream()
                .filter(r -> r.getPaymentType().equals("MEMBERSHIP"))
                .findFirst()
                .orElseThrow();
        assertThat(membership.getPaymentType()).isEqualTo("MEMBERSHIP");

        AdminPaymentHistoryResponse tokenPurchase = result.stream()
                .filter(r -> r.getPaymentType().equals("TOKEN_PURCHASE"))
                .findFirst()
                .orElseThrow();
        assertThat(tokenPurchase.getPaymentType()).isEqualTo("TOKEN_PURCHASE");
    }

    @Test
    @DisplayName("getAllPaymentHistory - createdAt 기준 정렬")
    void getAllPaymentHistory_SortedByCreatedAt() {
        // given
        PaymentHistory olderHistory = PaymentHistory.builder()
                .id(3L)
                .member(testMember1)
                .impUid("imp_older")
                .merchantUid("merchant_003")
                .amount(5000)
                .orderName("오래된 결제")
                .status(PaymentStatus.PAID)
                .createdAt(LocalDateTime.now().minusDays(10))
                .paidAt(LocalDateTime.now().minusDays(10))
                .build();

        PaymentHistory newerHistory = PaymentHistory.builder()
                .id(4L)
                .member(testMember1)
                .impUid("imp_newer")
                .merchantUid("merchant_004")
                .amount(5000)
                .orderName("최신 결제")
                .status(PaymentStatus.PAID)
                .createdAt(LocalDateTime.now().minusDays(1))
                .paidAt(LocalDateTime.now().minusDays(1))
                .build();

        List<PaymentHistory> paymentHistories = List.of(olderHistory, newerHistory);

        given(paymentRepository.findAll()).willReturn(Collections.emptyList());
        given(paymentHistoryRepository.findAll()).willReturn(paymentHistories);

        // when
        List<AdminPaymentHistoryResponse> result = adminService.getAllPaymentHistory(null);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        // 최신순 정렬 확인 (최신 것이 먼저)
        assertThat(result.get(0).getCreatedAt()).isAfter(result.get(1).getCreatedAt());
        assertThat(result.get(0).getImpUid()).isEqualTo("imp_newer");
    }

    @Test
    @DisplayName("getAllPaymentHistory - 빈 결과")
    void getAllPaymentHistory_Empty() {
        // given
        given(paymentRepository.findAll()).willReturn(Collections.emptyList());
        given(paymentHistoryRepository.findAll()).willReturn(Collections.emptyList());

        // when
        List<AdminPaymentHistoryResponse> result = adminService.getAllPaymentHistory(null);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("grantTokens - 성공 케이스")
    void grantTokens_Success() {
        // given
        int tokensToGrant = 1000;
        int currentTokens = 5000;
        int expectedNewTotal = currentTokens + tokensToGrant;

        testBilling1.setCurrentTokens(currentTokens);

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember1));
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.of(testBilling1));
        given(billingRepository.save(any(Billing.class))).willReturn(testBilling1);

        // when
        int result = adminService.grantTokens(1L, tokensToGrant, "테스트 토큰 부여");

        // then
        assertThat(result).isEqualTo(expectedNewTotal);
        assertThat(testBilling1.getCurrentTokens()).isEqualTo(expectedNewTotal);
        verify(billingRepository).save(testBilling1);
    }

    @Test
    @DisplayName("grantTokens - 사용자 없음 (IllegalArgumentException)")
    void grantTokens_UserNotFound() {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.grantTokens(999L, 1000, "테스트"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("grantTokens - Billing 없음 (BillingNotFoundException)")
    void grantTokens_BillingNotFound() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember1));
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.grantTokens(1L, 1000, "테스트"))
                .isInstanceOf(BillingNotFoundException.class);
    }

    @Test
    @DisplayName("grantTokens - 토큰 한도 초과 (IllegalStateException)")
    void grantTokens_ExceedsLimit() {
        // given
        int currentTokens = TokenConstants.MAX_TOKEN_LIMIT - 100;
        int tokensToGrant = 200; // 한도 초과
        testBilling1.setCurrentTokens(currentTokens);

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember1));
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.of(testBilling1));

        // when & then
        assertThatThrownBy(() -> adminService.grantTokens(1L, tokensToGrant, "테스트"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("토큰 보유량 한도를 초과할 수 없습니다");
    }

    @Test
    @DisplayName("grantTokens - 경계값: 최대 한도까지 부여")
    void grantTokens_MaxLimitBoundary() {
        // given
        int currentTokens = TokenConstants.MAX_TOKEN_LIMIT - 1000;
        int tokensToGrant = 1000; // 정확히 한도까지
        testBilling1.setCurrentTokens(currentTokens);

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember1));
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.of(testBilling1));
        given(billingRepository.save(any(Billing.class))).willReturn(testBilling1);

        // when
        int result = adminService.grantTokens(1L, tokensToGrant, "테스트");

        // then
        assertThat(result).isEqualTo(TokenConstants.MAX_TOKEN_LIMIT);
        assertThat(testBilling1.getCurrentTokens()).isEqualTo(TokenConstants.MAX_TOKEN_LIMIT);
    }

    @Test
    @DisplayName("grantTokens - 경계값: 한도 초과 시 에러 메시지 확인")
    void grantTokens_ExceedsLimitErrorMessage() {
        // given
        int currentTokens = TokenConstants.MAX_TOKEN_LIMIT - 50;
        int tokensToGrant = 100; // 한도 초과
        int availableSpace = TokenConstants.MAX_TOKEN_LIMIT - currentTokens;

        testBilling1.setCurrentTokens(currentTokens);

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember1));
        given(billingRepository.findByMember(testMember1)).willReturn(Optional.of(testBilling1));

        // when & then
        assertThatThrownBy(() -> adminService.grantTokens(1L, tokensToGrant, "테스트"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("토큰 보유량 한도를 초과할 수 없습니다")
                .hasMessageContaining(String.valueOf(currentTokens))
                .hasMessageContaining(String.valueOf(tokensToGrant))
                .hasMessageContaining(String.valueOf(TokenConstants.MAX_TOKEN_LIMIT))
                .hasMessageContaining(String.valueOf(availableSpace));
    }
}
