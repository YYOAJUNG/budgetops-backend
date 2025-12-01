package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.dto.AzureAccountCreateRequest;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureAccountServiceTest {

    @Mock
    private AzureAccountRepository repository;

    @Mock
    private AzureCredentialValidator credentialValidator;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AzureAccountService service;

    private static final Long MEMBER_ID = 1L;
    private Member member;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "validate", true);
        member = Member.builder()
                .id(MEMBER_ID)
                .email("user@example.com")
                .name("테스트 사용자")
                .build();
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    }

    @Test
    @DisplayName("새 Azure 계정 등록 시 자격 검증 후 저장된다")
    void createNewAccount_success() {
        AzureAccountCreateRequest request = AzureAccountCreateRequest.builder()
                .name("My Azure")
                .subscriptionId("sub-1")
                .tenantId("tenant-1")
                .clientId("client-1")
                .clientSecret("secret-1")
                .build();

        when(repository.findByClientIdAndSubscriptionId("client-1", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialValidator.isValid("tenant-1", "client-1", "secret-1", "sub-1"))
                .thenReturn(true);
        when(repository.save(any(AzureAccount.class))).thenAnswer(invocation -> {
            AzureAccount saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        AzureAccount result = service.createWithVerify(request, MEMBER_ID);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("My Azure");
        assertThat(result.getActive()).isTrue();

        verify(repository).save(any(AzureAccount.class));
    }

    @Test
    @DisplayName("이미 활성화된 계정이 존재하면 예외가 발생한다")
    void createExistingActiveAccount_fail() {
        AzureAccountCreateRequest request = AzureAccountCreateRequest.builder()
                .name("My Azure")
                .subscriptionId("sub-1")
                .tenantId("tenant-1")
                .clientId("client-1")
                .clientSecret("secret-1")
                .build();

        AzureAccount existing = new AzureAccount();
        existing.setActive(Boolean.TRUE);

        when(repository.findByClientIdAndSubscriptionId("client-1", "sub-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createWithVerify(request, MEMBER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된");
    }

    @Test
    @DisplayName("비활성 계정은 재활성화 및 갱신된다")
    void reactivateInactiveAccount_success() {
        AzureAccountCreateRequest request = AzureAccountCreateRequest.builder()
                .name("My Azure")
                .subscriptionId("sub-1")
                .tenantId("tenant-1")
                .clientId("client-1")
                .clientSecret("secret-1")
                .build();

        AzureAccount inactive = new AzureAccount();
        inactive.setId(42L);
        inactive.setActive(Boolean.FALSE);
        inactive.setClientSecretLast4("0000");

        when(repository.findByClientIdAndSubscriptionId("client-1", "sub-1"))
                .thenReturn(Optional.of(inactive));
        when(credentialValidator.isValid("tenant-1", "client-1", "secret-1", "sub-1"))
                .thenReturn(true);
        when(repository.save(any(AzureAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AzureAccount result = service.createWithVerify(request, MEMBER_ID);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getActive()).isTrue();
        assertThat(result.getClientSecretLast4()).isEqualTo("et-1");

        ArgumentCaptor<AzureAccount> captor = ArgumentCaptor.forClass(AzureAccount.class);
        verify(repository).save(captor.capture());
        AzureAccount saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("My Azure");
        assertThat(saved.getClientSecretLast4()).isEqualTo("et-1");
    }

    @Test
    @DisplayName("자격 증명이 유효하지 않으면 예외를 던진다")
    void createAccount_invalidCredential_fail() {
        AzureAccountCreateRequest request = AzureAccountCreateRequest.builder()
                .name("My Azure")
                .subscriptionId("sub-1")
                .tenantId("tenant-1")
                .clientId("client-1")
                .clientSecret("secret-1")
                .build();

        when(repository.findByClientIdAndSubscriptionId("client-1", "sub-1"))
                .thenReturn(Optional.empty());
        when(credentialValidator.isValid("tenant-1", "client-1", "secret-1", "sub-1"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createWithVerify(request, MEMBER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자격증명");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("활성 계정 목록 조회는 Repository를 위임한다")
    void getActiveAccounts() {
        AzureAccount account = new AzureAccount();
        account.setId(1L);

        when(repository.findByOwnerIdAndActiveTrue(MEMBER_ID)).thenReturn(List.of(account));

        assertThat(service.getActiveAccounts(MEMBER_ID)).containsExactly(account);
    }
}

