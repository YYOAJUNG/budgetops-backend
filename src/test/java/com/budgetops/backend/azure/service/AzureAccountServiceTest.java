package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.dto.AzureAccountCreateRequest;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.billing.entity.Workspace;
import com.budgetops.backend.billing.repository.WorkspaceRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureAccountServiceTest {

    private static final long MEMBER_ID = 1L;

    @Mock
    private AzureAccountRepository repository;

    @Mock
    private AzureCredentialValidator credentialValidator;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AzureAccountService service;

    private Member member;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "validate", true);

        member = new Member();
        member.setId(MEMBER_ID);
        member.setName("Tester");
        member.setEmail("tester@example.com");

        workspace = new Workspace();
        workspace.setId(99L);
        workspace.setOwner(member);
    }

    private void mockExistingWorkspace() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(workspaceRepository.findByOwner(member)).thenReturn(List.of(workspace));
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

        mockExistingWorkspace();
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
        assertThat(result.getWorkspace()).isEqualTo(workspace);
        assertThat(result.getActive()).isTrue();

        verify(repository).save(any(AzureAccount.class));
    }

    @Test
    @DisplayName("이미 활성화된 계정이라도 동일 사용자는 갱신할 수 있다")
    void createExistingActiveAccount_sameOwner_updates() {
        AzureAccountCreateRequest request = AzureAccountCreateRequest.builder()
                .name("My Azure")
                .subscriptionId("sub-1")
                .tenantId("tenant-1")
                .clientId("client-1")
                .clientSecret("secret-1")
                .build();

        mockExistingWorkspace();
        AzureAccount existing = new AzureAccount();
        existing.setId(777L);
        existing.setActive(Boolean.TRUE);
        existing.setWorkspace(workspace);

        when(repository.findByClientIdAndSubscriptionId("client-1", "sub-1"))
                .thenReturn(Optional.of(existing));
        when(credentialValidator.isValid("tenant-1", "client-1", "secret-1", "sub-1"))
                .thenReturn(true);
        when(repository.save(any(AzureAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AzureAccount result = service.createWithVerify(request, MEMBER_ID);

        assertThat(result.getId()).isEqualTo(777L);
        assertThat(result.getWorkspace()).isEqualTo(workspace);
        verify(repository).save(any(AzureAccount.class));
    }

    @Test
    @DisplayName("다른 사용자가 등록한 계정이면 예외가 발생한다")
    void createAccount_ownedByAnotherMember_fail() {
        AzureAccountCreateRequest request = AzureAccountCreateRequest.builder()
                .name("My Azure")
                .subscriptionId("sub-1")
                .tenantId("tenant-1")
                .clientId("client-1")
                .clientSecret("secret-1")
                .build();

        mockExistingWorkspace();
        AzureAccount existing = new AzureAccount();
        existing.setActive(Boolean.FALSE);
        Workspace otherWorkspace = new Workspace();
        Member otherMember = new Member();
        otherMember.setId(999L);
        otherWorkspace.setOwner(otherMember);
        existing.setWorkspace(otherWorkspace);

        when(repository.findByClientIdAndSubscriptionId("client-1", "sub-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createWithVerify(request, MEMBER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 사용자가 등록한");
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

        mockExistingWorkspace();
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
        assertThat(result.getWorkspace()).isEqualTo(workspace);
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

        mockExistingWorkspace();
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
    @DisplayName("활성 계정 목록 조회는 워크스페이스 기준으로 필터링된다")
    void getActiveAccounts() {
        AzureAccount account = new AzureAccount();
        account.setId(1L);

        mockExistingWorkspace();
        when(repository.findByWorkspaceIdAndActiveTrue(workspace.getId())).thenReturn(List.of(account));

        assertThat(service.getActiveAccounts(MEMBER_ID)).containsExactly(account);
    }
}
