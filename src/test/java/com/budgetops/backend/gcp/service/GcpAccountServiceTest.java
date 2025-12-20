package com.budgetops.backend.gcp.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.gcp.dto.*;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.entity.GcpResource;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.repository.GcpResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GcpAccount Service 테스트")
class GcpAccountServiceTest {

    @Mock
    private GcpServiceAccountVerifier serviceAccountVerifier;

    @Mock
    private GcpBillingAccountVerifier billingVerifier;

    @Mock
    private GcpAccountRepository gcpAccountRepository;

    @Mock
    private GcpResourceRepository gcpResourceRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private GcpAccountService gcpAccountService;

    private Member testMember;
    private GcpAccount testAccount;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");

        testAccount = new GcpAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test GCP Project");
        testAccount.setServiceAccountId("test-sa@test-project.iam.gserviceaccount.com");
        testAccount.setProjectId("test-project-12345");
        testAccount.setBillingAccountId("ABC123-DEF456-GHI789");
        testAccount.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("testServiceAccount - 서비스 계정 ID가 null인 경우")
    void testServiceAccount_NullServiceAccountId() {
        // given
        ServiceAccountTestRequest request = new ServiceAccountTestRequest();
        request.setServiceAccountId(null);
        request.setServiceAccountKeyJson("{}");

        // when & then
        assertThatThrownBy(() -> gcpAccountService.testServiceAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("서비스 계정 ID가 필요합니다");
    }

    @Test
    @DisplayName("testServiceAccount - 서비스 계정 키가 null인 경우")
    void testServiceAccount_NullKeyJson() {
        // given
        ServiceAccountTestRequest request = new ServiceAccountTestRequest();
        request.setServiceAccountId("test@project.iam.gserviceaccount.com");
        request.setServiceAccountKeyJson(null);

        // when & then
        assertThatThrownBy(() -> gcpAccountService.testServiceAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("서비스 계정 키 JSON이 필요합니다");
    }

    @Test
    @DisplayName("testBilling - 빌링 계정 ID가 null인 경우")
    void testBilling_NullBillingAccountId() {
        // given
        BillingAccountTestRequest request = new BillingAccountTestRequest();
        request.setBillingAccountId(null);
        request.setServiceAccountKeyJson("{}");

        // when & then
        assertThatThrownBy(() -> gcpAccountService.testBilling(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("빌링 계정 ID가 필요합니다");
    }

    @Test
    @DisplayName("testBilling - 잘못된 빌링 계정 ID 형식")
    void testBilling_InvalidBillingIdFormat() {
        // given
        BillingAccountTestRequest request = new BillingAccountTestRequest();
        request.setBillingAccountId("invalid-format");
        request.setServiceAccountKeyJson("{}");

        // when & then
        assertThatThrownBy(() -> gcpAccountService.testBilling(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잘못된 결제 계정 ID 형식입니다");
    }

    @Test
    @DisplayName("listAccounts - 계정 목록 조회 성공")
    void listAccounts_Success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(gcpAccountRepository.findByOwnerId(1L)).willReturn(List.of(testAccount));

        // when
        List<GcpAccountResponse> accounts = gcpAccountService.listAccounts(1L);

        // then
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getId()).isEqualTo(100L);
        assertThat(accounts.get(0).getName()).isEqualTo("Test GCP Project");
        assertThat(accounts.get(0).getProjectId()).isEqualTo("test-project-12345");
        assertThat(accounts.get(0).getServiceAccountName()).isEqualTo("test-sa");
        verify(gcpAccountRepository).findByOwnerId(1L);
    }

    @Test
    @DisplayName("listAccounts - 계정이 없는 경우")
    void listAccounts_Empty() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(gcpAccountRepository.findByOwnerId(1L)).willReturn(Collections.emptyList());

        // when
        List<GcpAccountResponse> accounts = gcpAccountService.listAccounts(1L);

        // then
        assertThat(accounts).isEmpty();
    }

    @Test
    @DisplayName("listAccounts - 존재하지 않는 회원")
    void listAccounts_MemberNotFound() {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gcpAccountService.listAccounts(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("deleteAccount - 계정 삭제 성공")
    void deleteAccount_Success() {
        // given
        given(gcpAccountRepository.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));
        given(gcpResourceRepository.findByGcpAccountId(100L)).willReturn(Collections.emptyList());

        // when
        gcpAccountService.deleteAccount(100L, 1L);

        // then
        verify(gcpAccountRepository).delete(testAccount);
        verify(gcpResourceRepository).findByGcpAccountId(100L);
    }

    @Test
    @DisplayName("deleteAccount - 연결된 리소스도 함께 삭제")
    void deleteAccount_WithResources() {
        // given
        GcpResource mockResource = new GcpResource();
        mockResource.setId(1L);
        
        given(gcpAccountRepository.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));
        given(gcpResourceRepository.findByGcpAccountId(100L)).willReturn(List.of(mockResource));

        // when
        gcpAccountService.deleteAccount(100L, 1L);

        // then
        verify(gcpResourceRepository).delete(mockResource);
        verify(gcpAccountRepository).delete(testAccount);
    }

    @Test
    @DisplayName("deleteAccount - 계정을 찾을 수 없음")
    void deleteAccount_NotFound() {
        // given
        given(gcpAccountRepository.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gcpAccountService.deleteAccount(999L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GCP 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("saveIntegration - 서비스 계정 ID가 null인 경우")
    void saveIntegration_NullServiceAccountId() {
        // given
        SaveIntegrationRequest request = new SaveIntegrationRequest();
        request.setServiceAccountId(null);
        request.setServiceAccountKeyJson("{}");

        // when
        SaveIntegrationResponse response = gcpAccountService.saveIntegration(request, 1L);

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("서비스 계정 ID가 필요합니다");
    }

    @Test
    @DisplayName("saveIntegration - 서비스 계정 키가 null인 경우")
    void saveIntegration_NullKeyJson() {
        // given
        SaveIntegrationRequest request = new SaveIntegrationRequest();
        request.setServiceAccountId("test@project.iam.gserviceaccount.com");
        request.setServiceAccountKeyJson(null);

        // when
        SaveIntegrationResponse response = gcpAccountService.saveIntegration(request, 1L);

        // then
        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("서비스 계정 키 JSON이 필요합니다");
    }

}

