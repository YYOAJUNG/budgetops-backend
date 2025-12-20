package com.budgetops.backend.ncp.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.ncp.dto.NcpAlert;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NcpAlert Service 테스트")
class NcpAlertServiceTest {

    @Mock
    private NcpAccountRepository accountRepository;

    @Mock
    private NcpServerService serverService;

    @Mock
    private NcpRuleLoader ruleLoader;

    @Mock
    private NcpMetricService metricService;

    @InjectMocks
    private NcpAlertService ncpAlertService;

    private NcpAccount testAccount;
    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);

        testAccount = new NcpAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test NCP Account");
        testAccount.setActive(Boolean.TRUE);
    }

    @Test
    @DisplayName("checkAllAccounts - 모든 계정 체크")
    void checkAllAccounts_Success() {
        // given
        given(accountRepository.findAll()).willReturn(List.of(testAccount));
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));
        given(serverService.listInstances(anyLong(), nullable(String.class))).willReturn(Collections.emptyList());
        given(ruleLoader.getAllRules()).willReturn(Collections.emptyList());

        // when
        List<NcpAlert> alerts = ncpAlertService.checkAllAccounts();

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
        assertThatThrownBy(() -> ncpAlertService.checkAccount(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NCP 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("checkAccount - 비활성화된 계정")
    void checkAccount_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));

        // when
        List<NcpAlert> alerts = ncpAlertService.checkAccount(100L);

        // then
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("checkAccount - 서버 조회 실패 시 빈 리스트 반환")
    void checkAccount_ServerFetchFails() {
        // given
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));
        given(serverService.listInstances(anyLong(), nullable(String.class))).willThrow(new RuntimeException("API 오류"));

        // when
        List<NcpAlert> alerts = ncpAlertService.checkAccount(100L);

        // then
        assertThat(alerts).isEmpty();
    }
}

