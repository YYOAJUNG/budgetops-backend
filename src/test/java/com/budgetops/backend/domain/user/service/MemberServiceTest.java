package com.budgetops.backend.domain.user.service;

import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsAccountService;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.azure.service.AzureAccountService;
import com.budgetops.backend.billing.service.BillingService;
import com.budgetops.backend.billing.service.PaymentService;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.service.GcpAccountService;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Member Service 테스트")
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BillingService billingService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private AwsAccountRepository awsAccountRepository;

    @Mock
    private AwsAccountService awsAccountService;

    @Mock
    private AzureAccountRepository azureAccountRepository;

    @Mock
    private AzureAccountService azureAccountService;

    @Mock
    private GcpAccountRepository gcpAccountRepository;

    @Mock
    private GcpAccountService gcpAccountService;

    @Mock
    private NcpAccountRepository ncpAccountRepository;

    @InjectMocks
    private MemberService memberService;

    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .lastLoginAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Test
    @DisplayName("OAuth 로그인 - 기존 회원 업데이트")
    void upsertOAuthMember_ExistingMember() {
        // given
        given(memberRepository.findByEmail("test@example.com"))
                .willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);

        // when
        Member result = memberService.upsertOAuthMember("test@example.com", "업데이트된 이름");

        // then
        assertThat(result.getName()).isEqualTo("업데이트된 이름");
        assertThat(result.getLastLoginAt()).isNotNull();
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("OAuth 로그인 - 신규 회원 생성")
    void upsertOAuthMember_NewMember() {
        // given
        Member newMember = Member.builder()
                .id(2L)
                .email("new@example.com")
                .name("신규 사용자")
                .lastLoginAt(LocalDateTime.now())
                .build();

        com.budgetops.backend.billing.entity.Billing billing = com.budgetops.backend.billing.entity.Billing.builder()
                .member(newMember)
                .currentPlan(com.budgetops.backend.billing.enums.BillingPlan.FREE)
                .currentPrice(0)
                .currentTokens(10000)
                .build();

        given(memberRepository.findByEmail("new@example.com"))
                .willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(newMember);
        given(billingService.initializeBilling(any(Member.class))).willReturn(billing);

        // when
        Member result = memberService.upsertOAuthMember("new@example.com", "신규 사용자");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("신규 사용자");
        assertThat(result.getLastLoginAt()).isNotNull();
        verify(billingService).initializeBilling(any(Member.class));
    }

    @Test
    @DisplayName("회원 탈퇴 - 모든 연관 리소스 삭제")
    void deleteMemberWithAssociations_Success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(awsAccountRepository.findByOwnerIdAndActiveTrue(1L))
                .willReturn(Collections.emptyList());
        given(azureAccountRepository.findByOwnerIdAndActiveTrue(1L))
                .willReturn(Collections.emptyList());
        given(gcpAccountRepository.findByOwnerId(1L))
                .willReturn(Collections.emptyList());
        given(ncpAccountRepository.findByOwnerId(1L))
                .willReturn(Collections.emptyList());
        doNothing().when(paymentService).deletePayment(any(Member.class));
        doNothing().when(billingService).deleteBilling(any(Member.class));
        doNothing().when(memberRepository).delete(any(Member.class));

        // when
        memberService.deleteMemberWithAssociations(1L);

        // then
        verify(paymentService).deletePayment(testMember);
        verify(billingService).deleteBilling(testMember);
        verify(memberRepository).delete(testMember);
    }

    @Test
    @DisplayName("회원 탈퇴 - 존재하지 않는 회원")
    void deleteMemberWithAssociations_MemberNotFound() {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.deleteMemberWithAssociations(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("회원 탈퇴 - AWS 계정 삭제 실패해도 계속 진행")
    void deleteMemberWithAssociations_AwsAccountDeletionFailure() {
        // given
        com.budgetops.backend.aws.entity.AwsAccount awsAccount = new com.budgetops.backend.aws.entity.AwsAccount();
        awsAccount.setId(100L);

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(awsAccountRepository.findByOwnerIdAndActiveTrue(1L))
                .willReturn(List.of(awsAccount));
        doThrow(new RuntimeException("AWS 계정 삭제 실패"))
                .when(awsAccountService).deactivateAccount(100L, 1L);
        given(azureAccountRepository.findByOwnerIdAndActiveTrue(1L))
                .willReturn(Collections.emptyList());
        given(gcpAccountRepository.findByOwnerId(1L))
                .willReturn(Collections.emptyList());
        given(ncpAccountRepository.findByOwnerId(1L))
                .willReturn(Collections.emptyList());
        doNothing().when(paymentService).deletePayment(any(Member.class));
        doNothing().when(billingService).deleteBilling(any(Member.class));
        doNothing().when(memberRepository).delete(any(Member.class));

        // when
        memberService.deleteMemberWithAssociations(1L);

        // then
        // 예외가 발생해도 계속 진행되어야 함
        verify(memberRepository).delete(testMember);
    }
}

