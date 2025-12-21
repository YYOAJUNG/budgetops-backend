package com.budgetops.backend.ncp.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.ncp.dto.NcpAccountCreateRequest;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NcpAccount Service 테스트")
class NcpAccountServiceTest {

    @Mock
    private NcpAccountRepository accountRepo;

    @Mock
    private NcpCredentialValidator credentialValidator;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private NcpAccountService ncpAccountService;

    private Member testMember;
    private NcpAccount testAccount;
    private NcpAccountCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ncpAccountService, "validate", false);

        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");

        testAccount = new NcpAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test NCP Account");
        testAccount.setAccessKey("NCPACCESSKEY123456");
        testAccount.setSecretKeyEnc("ncpSecretKey123456789");
        testAccount.setSecretKeyLast4("6789");
        testAccount.setRegionCode("KR");
        testAccount.setActive(Boolean.TRUE);

        createRequest = new NcpAccountCreateRequest();
        createRequest.setName("New NCP Account");
        createRequest.setAccessKey("NEWNCPKEY123456");
        createRequest.setSecretKey("newNcpSecretKey123456789");
        createRequest.setRegionCode("KR");
    }

    @Test
    @DisplayName("createWithVerify - 새 계정 생성 성공")
    void createWithVerify_NewAccount_Success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKey(anyString())).willReturn(Optional.empty());
        given(accountRepo.save(any(NcpAccount.class))).willAnswer(invocation -> {
            NcpAccount account = invocation.getArgument(0);
            account.setId(101L);
            return account;
        });

        // when
        NcpAccount result = ncpAccountService.createWithVerify(createRequest, 1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getName()).isEqualTo("New NCP Account");
        assertThat(result.getActive()).isTrue();
        verify(accountRepo).save(any(NcpAccount.class));
    }

    @Test
    @DisplayName("createWithVerify - 비활성 계정 재활성화")
    void createWithVerify_InactiveAccount_Reactivate() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKey(createRequest.getAccessKey()))
                .willReturn(Optional.of(testAccount));
        given(accountRepo.save(any(NcpAccount.class))).willReturn(testAccount);
        given(accountRepo.findById(100L)).willReturn(Optional.of(testAccount));

        // when
        NcpAccount result = ncpAccountService.createWithVerify(createRequest, 1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getActive()).isTrue();
        verify(accountRepo).save(testAccount);
        verify(accountRepo).findById(100L);
    }

    @Test
    @DisplayName("createWithVerify - 이미 활성화된 계정 등록 시도")
    void createWithVerify_ActiveAccount_ThrowsException() {
        // given
        testAccount.setActive(Boolean.TRUE);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKey(createRequest.getAccessKey()))
                .willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> ncpAccountService.createWithVerify(createRequest, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된 accessKey 입니다");
    }

    @Test
    @DisplayName("createWithVerify - 존재하지 않는 회원")
    void createWithVerify_MemberNotFound() {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ncpAccountService.createWithVerify(createRequest, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("createWithVerify - 자격증명 검증 활성화 시 유효하지 않은 자격증명")
    void createWithVerify_InvalidCredentials() {
        // given
        ReflectionTestUtils.setField(ncpAccountService, "validate", true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKey(anyString())).willReturn(Optional.empty());
        given(credentialValidator.isValid(anyString(), anyString(), anyString())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> ncpAccountService.createWithVerify(createRequest, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NCP 자격증명이 유효하지 않습니다");
    }

    @Test
    @DisplayName("getActiveAccounts - 활성 계정 목록 조회")
    void getActiveAccounts_Success() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByOwnerIdAndActiveTrue(1L)).willReturn(List.of(testAccount));

        // when
        List<NcpAccount> accounts = ncpAccountService.getActiveAccounts(1L);

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
        List<NcpAccount> accounts = ncpAccountService.getActiveAccounts(1L);

        // then
        assertThat(accounts).isEmpty();
    }

    @Test
    @DisplayName("getAccountInfo - 계정 정보 조회 성공")
    void getAccountInfo_Success() {
        // given
        given(accountRepo.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));

        // when
        NcpAccount account = ncpAccountService.getAccountInfo(100L, 1L);

        // then
        assertThat(account).isNotNull();
        assertThat(account.getId()).isEqualTo(100L);
        assertThat(account.getName()).isEqualTo("Test NCP Account");
    }

    @Test
    @DisplayName("getAccountInfo - 계정을 찾을 수 없음")
    void getAccountInfo_NotFound() {
        // given
        given(accountRepo.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ncpAccountService.getAccountInfo(999L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("deactivateAccount - 계정 비활성화 성공")
    void deactivateAccount_Success() {
        // given
        given(accountRepo.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));
        given(accountRepo.save(any(NcpAccount.class))).willReturn(testAccount);

        // when
        ncpAccountService.deactivateAccount(100L, 1L);

        // then
        verify(accountRepo).save(testAccount);
    }

    @Test
    @DisplayName("deactivateAccount - 이미 비활성화된 계정")
    void deactivateAccount_AlreadyInactive() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepo.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));

        // when
        ncpAccountService.deactivateAccount(100L, 1L);

        // then
        verify(accountRepo, never()).save(any(NcpAccount.class));
    }

    @Test
    @DisplayName("deactivateAccount - 계정을 찾을 수 없음")
    void deactivateAccount_NotFound() {
        // given
        given(accountRepo.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ncpAccountService.deactivateAccount(999L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("createWithVerify - accessKey trim 처리")
    void createWithVerify_TrimAccessKey() {
        // given
        createRequest.setAccessKey("  TRIMTEST123  ");
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(accountRepo.findByAccessKey("TRIMTEST123")).willReturn(Optional.empty());
        given(accountRepo.save(any(NcpAccount.class))).willAnswer(invocation -> {
            NcpAccount account = invocation.getArgument(0);
            account.setId(101L);
            return account;
        });

        // when
        ncpAccountService.createWithVerify(createRequest, 1L);

        // then
        verify(accountRepo).findByAccessKey("TRIMTEST123");
    }
}

