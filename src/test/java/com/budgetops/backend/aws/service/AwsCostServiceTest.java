package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.domain.user.entity.Member;
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
@DisplayName("AwsCost Service 테스트")
class AwsCostServiceTest {

    @Mock
    private AwsAccountRepository accountRepository;

    @Mock
    private AwsUsageService usageService;

    @InjectMocks
    private AwsCostService awsCostService;

    private Member testMember;
    private AwsAccount testAccount;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);

        testAccount = new AwsAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test AWS Account");
        testAccount.setAccessKeyId("AKIATEST");
        testAccount.setSecretKeyEnc("secretkey");
        testAccount.setDefaultRegion("ap-northeast-2");
        testAccount.setActive(Boolean.TRUE);
    }

    @Test
    @DisplayName("getCosts - 존재하지 않는 계정")
    void getCosts_AccountNotFound() {
        // given
        given(accountRepository.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsCostService.getCosts(999L, 1L, "2024-01-01", "2024-01-31"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("getCosts - 비활성화된 계정")
    void getCosts_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> awsCostService.getCosts(100L, 1L, "2024-01-01", "2024-01-31"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }

    @Test
    @DisplayName("getMonthlyCost - 존재하지 않는 계정")
    void getMonthlyCost_AccountNotFound() {
        // given
        given(accountRepository.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsCostService.getMonthlyCost(999L, 1L, 2024, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("getMonthlyCost - 비활성화된 계정")
    void getMonthlyCost_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> awsCostService.getMonthlyCost(100L, 1L, 2024, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }
}

