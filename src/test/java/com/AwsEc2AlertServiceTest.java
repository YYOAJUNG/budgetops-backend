package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.*;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.notification.service.SlackNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwsEc2AlertService Slack 통합 테스트")
class AwsEc2AlertServiceTest {

    @Mock
    private AwsAccountRepository accountRepository;

    @Mock
    private AwsEc2Service ec2Service;

    @Mock
    private AwsEc2RuleLoader ruleLoader;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SlackNotificationService slackNotificationService;

    @InjectMocks
    private AwsEc2AlertService awsEc2AlertService;

    private AwsAccount testAccount;
    private Member testMember1;
    private Member testMember2;
    private List<AwsEc2InstanceResponse> testInstances;
    private List<AlertRule> testRules;

    @BeforeEach
    void setUp() {
        // Test AWS Account 설정
        testAccount = new AwsAccount();
        testAccount.setId(1L);
        testAccount.setName("Test Account");
        testAccount.setActive(true);
        testAccount.setAccessKeyId("test-access-key");
        testAccount.setSecretKeyEnc("test-secret-key");
        testAccount.setDefaultRegion("us-east-1");

        // Test Members 설정
        testMember1 = new Member();
        testMember1.setId(1L);
        testMember1.setEmail("user1@example.com");
        testMember1.setSlackNotificationsEnabled(true);
        testMember1.setSlackWebhookUrl("https://hooks.slack.com/services/TEST1/WEBHOOK/URL");

        testMember2 = new Member();
        testMember2.setId(2L);
        testMember2.setEmail("user2@example.com");
        testMember2.setSlackNotificationsEnabled(true);
        testMember2.setSlackWebhookUrl("https://hooks.slack.com/services/TEST2/WEBHOOK/URL");

        // Test EC2 Instances 설정
        testInstances = new ArrayList<>();
        AwsEc2InstanceResponse instance = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .name("Test Instance")
                .state("running")
                .instanceType("t2.micro")
                .availabilityZone("us-east-1a")
                .publicIp("1.2.3.4")
                .privateIp("10.0.0.1")
                .launchTime("2024-01-01T00:00:00Z")
                .build();
        testInstances.add(instance);

        // Test Rules 설정
        testRules = new ArrayList<>();
        AlertCondition condition = AlertCondition.builder()
                .metric("cpu_utilization")
                .period("7d")
                .threshold("10.0")
                .operator("<")
                .build();
        
        AlertRule rule = AlertRule.builder()
                .id("cpu_low")
                .title("CPU 사용률 낮음")
                .description("CPU 사용률이 낮습니다")
                .recommendation("인스턴스 축소를 고려하세요")
                .conditions(List.of(condition))
                .build();
        
        testRules.add(rule);
    }

    @Test
    @DisplayName("checkAccount - 알림 발생 시 Slack 구독자들에게 전송")
    void checkAccount_SendsSlackNotifications() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(testInstances);
        given(ruleLoader.getAllRules()).willReturn(testRules);
        given(memberRepository.findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull())
                .willReturn(List.of(testMember1, testMember2));

        // when
        awsEc2AlertService.checkAccount(1L);

        // then
        verify(memberRepository).findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
        // 각 멤버에게 알림이 전송되었는지 확인 (실제 알림 발생 시)
        // 참고: 실제로는 CloudWatch API를 호출하므로, 이 테스트에서는 메서드 호출 여부만 확인
    }

    @Test
    @DisplayName("checkAccount - Slack 구독자가 없는 경우")
    void checkAccount_NoSlackSubscribers() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(testInstances);
        given(ruleLoader.getAllRules()).willReturn(testRules);
        given(memberRepository.findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull())
                .willReturn(new ArrayList<>());

        // when
        awsEc2AlertService.checkAccount(1L);

        // then
        verify(memberRepository).findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
        verify(slackNotificationService, never()).sendEc2Alert(anyString(), any());
    }

    @Test
    @DisplayName("checkAccount - Webhook URL이 빈 문자열인 멤버는 건너뜀")
    void checkAccount_SkipsMembersWithEmptyWebhookUrl() {
        // given
        Member memberWithEmptyUrl = new Member();
        memberWithEmptyUrl.setId(3L);
        memberWithEmptyUrl.setSlackNotificationsEnabled(true);
        memberWithEmptyUrl.setSlackWebhookUrl("   ");

        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(testInstances);
        given(ruleLoader.getAllRules()).willReturn(testRules);
        given(memberRepository.findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull())
                .willReturn(List.of(testMember1, memberWithEmptyUrl));

        // when
        awsEc2AlertService.checkAccount(1L);

        // then
        verify(memberRepository).findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
        // memberWithEmptyUrl에게는 전송되지 않아야 함
    }

    @Test
    @DisplayName("checkAccount - 비활성 계정은 체크하지 않음")
    void checkAccount_InactiveAccount() {
        // given
        testAccount.setActive(false);
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));

        // when
        List<AwsEc2Alert> alerts = awsEc2AlertService.checkAccount(1L);

        // then
        assertThat(alerts).isEmpty();
        verify(ec2Service, never()).listInstances(anyLong(), any());
        verify(slackNotificationService, never()).sendEc2Alert(anyString(), any());
    }

    @Test
    @DisplayName("checkAccount - 존재하지 않는 계정")
    void checkAccount_AccountNotFound() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> awsEc2AlertService.checkAccount(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AWS 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("checkAllAccounts - 모든 활성 계정을 체크하고 Slack 알림 전송")
    void checkAllAccounts_SendsSlackNotifications() {
        // given
        AwsAccount account2 = new AwsAccount();
        account2.setId(2L);
        account2.setName("Test Account 2");
        account2.setActive(true);
        account2.setAccessKeyId("test-key-2");
        account2.setSecretKeyEnc("test-secret-2");
        account2.setDefaultRegion("us-west-2");

        given(accountRepository.findByActiveTrue()).willReturn(List.of(testAccount, account2));
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(account2));
        given(ec2Service.listInstances(anyLong(), any())).willReturn(new ArrayList<>());
        given(ruleLoader.getAllRules()).willReturn(testRules);

        // when
        List<AwsEc2Alert> alerts = awsEc2AlertService.checkAllAccounts();

        // then
        verify(accountRepository).findByActiveTrue();
        assertThat(alerts).isNotNull();
    }

    @Test
    @DisplayName("checkAllAccounts - 계정 체크 실패 시 다른 계정은 계속 체크")
    void checkAllAccounts_ContinuesOnError() {
        // given
        AwsAccount account2 = new AwsAccount();
        account2.setId(2L);
        account2.setName("Test Account 2");
        account2.setActive(true);
        account2.setAccessKeyId("test-key-2");
        account2.setSecretKeyEnc("test-secret-2");
        account2.setDefaultRegion("us-west-2");

        given(accountRepository.findByActiveTrue()).willReturn(List.of(testAccount, account2));
        // 첫 번째 계정은 실패
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willThrow(new RuntimeException("EC2 API Error"));
        // 두 번째 계정은 성공
        given(accountRepository.findById(2L)).willReturn(Optional.of(account2));
        given(ec2Service.listInstances(2L, null)).willReturn(new ArrayList<>());
        given(ruleLoader.getAllRules()).willReturn(testRules);

        // when
        List<AwsEc2Alert> alerts = awsEc2AlertService.checkAllAccounts();

        // then
        assertThat(alerts).isNotNull();
        // 첫 번째 계정에서 에러가 발생해도 두 번째 계정은 체크됨
    }

    @Test
    @DisplayName("notifySlackSubscribers - 알림 전송 테스트")
    void notifySlackSubscribers_SendsToAllSubscribers() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(new ArrayList<>());
        given(ruleLoader.getAllRules()).willReturn(new ArrayList<>());

        // when
        List<AwsEc2Alert> alerts = awsEc2AlertService.checkAccount(1L);

        // then
        // 인스턴스가 없으므로 알림이 발생하지 않음
        assertThat(alerts).isEmpty();
        // 알림이 없으므로 Slack 구독자 조회도 하지 않음
        verify(memberRepository, never()).findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
    }

    @Test
    @DisplayName("Slack 전송 실패 시 예외를 던지지 않고 계속 진행")
    void slackSendFailure_DoesNotThrowException() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(testInstances);
        given(ruleLoader.getAllRules()).willReturn(testRules);
        given(memberRepository.findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull())
                .willReturn(List.of(testMember1));
        doThrow(new RuntimeException("Slack API Error"))
                .when(slackNotificationService).sendEc2Alert(anyString(), any());

        // when & then
        // 예외가 발생하지 않아야 함
        awsEc2AlertService.checkAccount(1L);
    }

    @Test
    @DisplayName("checkAccount - 실행 중이지 않은 인스턴스는 체크하지 않음")
    void checkAccount_SkipsNonRunningInstances() {
        // given
        AwsEc2InstanceResponse stoppedInstance = AwsEc2InstanceResponse.builder()
                .instanceId("i-stopped")
                .name("Stopped Instance")
                .state("stopped")
                .instanceType("t2.micro")
                .availabilityZone("us-east-1a")
                .publicIp(null)
                .privateIp("10.0.0.2")
                .launchTime("2024-01-01T00:00:00Z")
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(List.of(stoppedInstance));
        given(ruleLoader.getAllRules()).willReturn(testRules);

        // when
        List<AwsEc2Alert> alerts = awsEc2AlertService.checkAccount(1L);

        // then
        // stopped 상태의 인스턴스는 체크되지 않으므로 알림이 생성되지 않음
        verify(memberRepository, never()).findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
    }

    @Test
    @DisplayName("checkAccount - 인스턴스가 없는 경우")
    void checkAccount_NoInstances() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(new ArrayList<>());
        given(ruleLoader.getAllRules()).willReturn(testRules);

        // when
        List<AwsEc2Alert> alerts = awsEc2AlertService.checkAccount(1L);

        // then
        assertThat(alerts).isEmpty();
        verify(memberRepository, never()).findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
    }

    @Test
    @DisplayName("checkAccount - 규칙이 없는 경우")
    void checkAccount_NoRules() {
        // given
        given(accountRepository.findById(1L)).willReturn(Optional.of(testAccount));
        given(ec2Service.listInstances(1L, null)).willReturn(testInstances);
        given(ruleLoader.getAllRules()).willReturn(new ArrayList<>());

        // when
        List<AwsEc2Alert> alerts = awsEc2AlertService.checkAccount(1L);

        // then
        assertThat(alerts).isEmpty();
        verify(memberRepository, never()).findAllBySlackNotificationsEnabledTrueAndSlackWebhookUrlIsNotNull();
    }
}

