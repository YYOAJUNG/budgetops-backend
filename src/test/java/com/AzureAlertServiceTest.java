package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.dto.AzureAlert;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.domain.user.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AzureAlert Service 테스트")
class AzureAlertServiceTest {

    @Mock
    private AzureAccountRepository accountRepository;

    @Mock
    private AzureComputeService computeService;

    @Mock
    private AzureRuleLoader ruleLoader;

    @InjectMocks
    private AzureAlertService azureAlertService;

    private AzureAccount testAccount;
    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);

        testAccount = new AzureAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test Azure Account");
        testAccount.setSubscriptionId("sub-12345");
        testAccount.setActive(Boolean.TRUE);
    }

    @Test
    @DisplayName("checkAllAccounts - 모든 계정 체크")
    void checkAllAccounts_Success() {
        // given
        given(accountRepository.findAll()).willReturn(List.of(testAccount));
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));
        given(computeService.listVirtualMachines(anyLong(), anyString())).willReturn(Collections.emptyList());
        given(ruleLoader.getAllRules()).willReturn(Collections.emptyList());

        // when
        List<AzureAlert> alerts = azureAlertService.checkAllAccounts();

        // then
        assertThat(alerts).isNotNull();
        verify(accountRepository).findAll();
    }

    @Test
    @DisplayName("checkAccount - 존재하지 않는 계정")
    void checkAccount_AccountNotFound() {
        // given
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> azureAlertService.checkAccount(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Azure 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("checkAccount - 비활성화된 계정")
    void checkAccount_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));

        // when
        List<AzureAlert> alerts = azureAlertService.checkAccount(100L);

        // then
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("checkAccount - VM 조회 실패 시 빈 리스트 반환")
    void checkAccount_VmFetchFails() {
        // given
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));
        given(computeService.listVirtualMachines(anyLong(), anyString())).willThrow(new RuntimeException("API 오류"));
        given(ruleLoader.getAllRules()).willReturn(Collections.emptyList());

        // when
        List<AzureAlert> alerts = azureAlertService.checkAccount(100L);

        // then
        assertThat(alerts).isEmpty();
    }
}

