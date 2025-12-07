package com.budgetops.backend.gcp.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("GcpCost Service 테스트")
class GcpCostServiceTest {

    @Mock
    private GcpAccountRepository accountRepository;

    @InjectMocks
    private GcpCostService gcpCostService;

    private Member testMember;
    private GcpAccount testAccount;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);

        testAccount = new GcpAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test GCP Project");
        testAccount.setProjectId("test-project");
    }

    @Test
    @DisplayName("getAccountCosts - 존재하지 않는 계정")
    void getAccountCosts_AccountNotFound() {
        // given
        given(accountRepository.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> gcpCostService.getAccountCosts(999L, 1L, "2024-01-01", "2024-01-31"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GCP 계정을 찾을 수 없습니다");
    }
}

