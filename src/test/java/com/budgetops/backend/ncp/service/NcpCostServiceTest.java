package com.budgetops.backend.ncp.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("NcpCost Service 테스트")
class NcpCostServiceTest {

    @Mock
    private NcpAccountRepository accountRepository;

    @InjectMocks
    private NcpCostService ncpCostService;

    private Member testMember;
    private NcpAccount testAccount;

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
    @DisplayName("getCostSummary - 존재하지 않는 계정")
    void getCostSummary_AccountNotFound() {
        // given
        given(accountRepository.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ncpCostService.getCostSummary(999L, 1L, "202412"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NCP 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("getCostSummary - 비활성화된 계정")
    void getCostSummary_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> ncpCostService.getCostSummary(100L, 1L, "202412"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }
}

