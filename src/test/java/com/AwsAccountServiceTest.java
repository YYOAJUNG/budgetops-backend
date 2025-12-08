package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.repository.AwsResourceRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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
@DisplayName("AwsAccount Service 테스트")
class AwsAccountServiceTest {

    @Mock
    private AwsAccountRepository accountRepo;

    @Mock
    private AwsCredentialValidator credentialValidator;

    @Mock
    private AwsResourceRepository resourceRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AwsAccountService awsAccountService;

    private Member testMember;
    private AwsAccount testAccount;
    private AwsAccountCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        // validate 플래그를 false로 설정 (테스트에서는 실제 AWS 검증 skip)
        ReflectionTestUtils.setField(awsAccountService, "validate", false);

        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");

        testAccount = new AwsAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test AWS Account");
        testAccount.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        testAccount.setSecretKeyEnc("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        testAccount.setSecretKeyLast4("EKEY");
        testAccount.setDefaultRegion("ap-northeast-2");
        testAccount.setActive(Boolean.TRUE);

        createRequest = new AwsAccountCreateRequest();
        createRequest.setName("New AWS Account");
        createRequest.setAccessKeyId("AKIAIOSFODNN7NEWKEY");
        createRequest.setSecretAccessKey("newSecretAccessKeyExample123456789");
        createRequest.setDefaultRegion("us-east-1");
    }

    @Test
    @DisplayName("createWithVerify - 새 계정 생성 성공")
    void createWithVerify_NewAccount_Success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKeyId(anyString())).willReturn(Optional.empty());
        given(accountRepo.save(any(AwsAccount.class))).willAnswer(invocation -> {
            AwsAccount account = invocation.getArgument(0);
            account.setId(101L);
            return account;
        });

        // when
        AwsAccount result = awsAccountService.createWithVerify(createRequest, 1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getName()).isEqualTo("New AWS Account");
        assertThat(result.getActive()).isTrue();
        verify(accountRepo).save(any(AwsAccount.class));
    }

    @Test
    @DisplayName("createWithVerify - 기존 계정 재활성화")
    void createWithVerify_ExistingAccount_Reactivate() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKeyId(createRequest.getAccessKeyId()))
                .willReturn(Optional.of(testAccount));
        given(accountRepo.save(any(AwsAccount.class))).willReturn(testAccount);

        // when
        AwsAccount result = awsAccountService.createWithVerify(createRequest, 1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getActive()).isTrue();
        assertThat(result.getName()).isEqualTo("New AWS Account");
        verify(accountRepo).save(testAccount);
    }

    @Test
    @DisplayName("createWithVerify - 다른 회원의 계정 재할당")
    void createWithVerify_ReassignToNewMember() {
        // given
        Member otherMember = new Member();
        otherMember.setId(2L);
        otherMember.setEmail("other@example.com");
        testAccount.setOwner(otherMember);

        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKeyId(createRequest.getAccessKeyId()))
                .willReturn(Optional.of(testAccount));
        given(accountRepo.save(any(AwsAccount.class))).willReturn(testAccount);

        // when
        AwsAccount result = awsAccountService.createWithVerify(createRequest, 1L);

        // then
        assertThat(result.getOwner().getId()).isEqualTo(1L);
        verify(accountRepo).save(testAccount);
    }

    @Test
    @DisplayName("createWithVerify - 존재하지 않는 회원")
    void createWithVerify_MemberNotFound() {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsAccountService.createWithVerify(createRequest, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("createWithVerify - 자격증명 검증 활성화 시 유효하지 않은 자격증명")
    void createWithVerify_InvalidCredentials() {
        // given
        ReflectionTestUtils.setField(awsAccountService, "validate", true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKeyId(anyString())).willReturn(Optional.empty());
        given(credentialValidator.isValid(anyString(), anyString(), anyString())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> awsAccountService.createWithVerify(createRequest, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AWS 자격증명이 유효하지 않습니다");
    }

    @Test
    @DisplayName("getActiveAccounts - 활성 계정 목록 조회")
    void getActiveAccounts_Success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByOwnerIdAndActiveTrue(1L)).willReturn(List.of(testAccount));

        // when
        List<AwsAccount> accounts = awsAccountService.getActiveAccounts(1L);

        // then
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getActive()).isTrue();
        verify(accountRepo).findByOwnerIdAndActiveTrue(1L);
    }

    @Test
    @DisplayName("getActiveAccounts - 활성 계정이 없는 경우")
    void getActiveAccounts_Empty() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByOwnerIdAndActiveTrue(1L)).willReturn(Collections.emptyList());

        // when
        List<AwsAccount> accounts = awsAccountService.getActiveAccounts(1L);

        // then
        assertThat(accounts).isEmpty();
    }

    @Test
    @DisplayName("getAccountInfo - 계정 정보 조회 성공")
    void getAccountInfo_Success() {
        // given
        given(accountRepo.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));

        // when
        AwsAccount account = awsAccountService.getAccountInfo(100L, 1L);

        // then
        assertThat(account).isNotNull();
        assertThat(account.getId()).isEqualTo(100L);
        assertThat(account.getName()).isEqualTo("Test AWS Account");
    }

    @Test
    @DisplayName("getAccountInfo - 계정을 찾을 수 없음")
    void getAccountInfo_NotFound() {
        // given
        given(accountRepo.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsAccountService.getAccountInfo(999L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("deactivateAccount - 계정 비활성화 성공")
    void deactivateAccount_Success() {
        // given
        given(accountRepo.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));
        given(resourceRepository.findByAwsAccountId(100L)).willReturn(Collections.emptyList());

        // when
        awsAccountService.deactivateAccount(100L, 1L);

        // then
        verify(accountRepo).delete(testAccount);
        verify(resourceRepository).findByAwsAccountId(100L);
    }

    @Test
    @DisplayName("deactivateAccount - 연결된 리소스도 함께 삭제")
    void deactivateAccount_WithResources() {
        // given
        com.budgetops.backend.aws.entity.AwsResource mockResource = new com.budgetops.backend.aws.entity.AwsResource();
        mockResource.setId(1L);
        
        given(accountRepo.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));
        given(resourceRepository.findByAwsAccountId(100L)).willReturn(List.of(mockResource));

        // when
        awsAccountService.deactivateAccount(100L, 1L);

        // then
        verify(resourceRepository).deleteAll(anyList());
        verify(accountRepo).delete(testAccount);
    }

    @Test
    @DisplayName("deactivateAccount - 계정을 찾을 수 없음")
    void deactivateAccount_NotFound() {
        // given
        given(accountRepo.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsAccountService.deactivateAccount(999L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("계정을 찾을 수 없습니다");
    }
}

