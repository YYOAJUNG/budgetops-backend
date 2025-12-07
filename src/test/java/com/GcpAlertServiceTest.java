package com.budgetops.backend.gcp.service;

import com.budgetops.backend.aws.dto.AlertRule;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.gcp.dto.GcpAlert;
import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GcpAlert Service 테스트")
class GcpAlertServiceTest {

    @Mock
    private GcpAccountRepository accountRepository;

    @Mock
    private GcpResourceService resourceService;

    @Mock
    private GcpRuleLoader ruleLoader;

    @InjectMocks
    private GcpAlertService gcpAlertService;

    private GcpAccount testAccount;
    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);

        testAccount = new GcpAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test GCP Account");
        testAccount.setProjectId("test-project");
    }

    @Test
    @DisplayName("checkAllAccounts - 모든 계정 체크")
    void checkAllAccounts_Success() {
        // given
        given(accountRepository.findAll()).willReturn(List.of(testAccount));
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));
        given(ruleLoader.getAllRules()).willReturn(Collections.emptyList());
        
        GcpResourceListResponse response = new GcpResourceListResponse();
        response.setResources(Collections.emptyList());
        given(resourceService.listResources(anyLong(), anyLong())).willReturn(response);

        // when
        List<GcpAlert> alerts = gcpAlertService.checkAllAccounts();

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
        assertThatThrownBy(() -> gcpAlertService.checkAccount(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GCP 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("checkAccount - 리소스 조회 실패 시 빈 리스트 반환")
    void checkAccount_ResourceFetchFails() {
        // given
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));
        given(resourceService.listResources(anyLong(), anyLong())).willThrow(new RuntimeException("API 오류"));
        given(ruleLoader.getAllRules()).willReturn(Collections.emptyList());

        // when
        List<GcpAlert> alerts = gcpAlertService.checkAccount(100L);

        // then
        assertThat(alerts).isEmpty();
    }
}

