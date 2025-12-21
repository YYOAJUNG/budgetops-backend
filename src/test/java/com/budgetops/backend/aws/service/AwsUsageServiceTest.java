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
@DisplayName("AwsUsage Service 테스트")
class AwsUsageServiceTest {

    @Mock
    private AwsAccountRepository accountRepository;

    @InjectMocks
    private AwsUsageService awsUsageService;

    private AwsAccount testAccount;
    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");

        testAccount = new AwsAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setActive(Boolean.TRUE);
        testAccount.setDefaultRegion("us-east-1");
        testAccount.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        testAccount.setSecretKeyEnc("secret");
    }

    @Test
    @DisplayName("getEc2Usage - 존재하지 않는 계정")
    void getEc2Usage_AccountNotFound() {
        // given
        given(accountRepository.findByIdAndOwnerId(999L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsUsageService.getEc2Usage(999L, 1L, "2024-01-01", "2024-01-31"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("getEc2Usage - 비활성화된 계정")
    void getEc2Usage_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findByIdAndOwnerId(100L, 1L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> awsUsageService.getEc2Usage(100L, 1L, "2024-01-01", "2024-01-31"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }

    @Test
    @DisplayName("getUsageMetrics - 존재하지 않는 계정")
    void getUsageMetrics_AccountNotFound() {
        // given
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsUsageService.getUsageMetrics(999L, "EC2", "2024-01-01", "2024-01-31"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("getUsageMetrics - 비활성화된 계정")
    void getUsageMetrics_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> awsUsageService.getUsageMetrics(100L, "EC2", "2024-01-01", "2024-01-31"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }
}

