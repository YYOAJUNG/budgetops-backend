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
@DisplayName("AwsEc2 Service 테스트")
class AwsEc2ServiceTest {

    @Mock
    private AwsAccountRepository accountRepository;

    @InjectMocks
    private AwsEc2Service awsEc2Service;

    private Member testMember;
    private AwsAccount testAccount;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");

        testAccount = new AwsAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test AWS Account");
        testAccount.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        testAccount.setSecretKeyEnc("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        testAccount.setDefaultRegion("ap-northeast-2");
        testAccount.setActive(Boolean.TRUE);
    }

    @Test
    @DisplayName("listInstances - 존재하지 않는 계정")
    void listInstances_AccountNotFound() {
        // given
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsEc2Service.listInstances(999L, "us-east-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("listInstances - 비활성화된 계정")
    void listInstances_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> awsEc2Service.listInstances(100L, "us-east-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }

    @Test
    @DisplayName("getEc2Instance - 존재하지 않는 계정")
    void getEc2Instance_AccountNotFound() {
        // given
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsEc2Service.getEc2Instance(999L, "i-123456", "us-east-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("getInstanceMetrics - 존재하지 않는 계정")
    void getInstanceMetrics_AccountNotFound() {
        // given
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsEc2Service.getInstanceMetrics(999L, "i-123456", "us-east-1", 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("getInstanceMetrics - 비활성화된 계정")
    void getInstanceMetrics_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> awsEc2Service.getInstanceMetrics(100L, "i-123456", "us-east-1", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }
}

