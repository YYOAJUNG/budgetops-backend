package com.budgetops.backend.budget.service;

import com.budgetops.backend.budget.dto.BudgetAlertResponse;
import com.budgetops.backend.budget.dto.BudgetSettingsRequest;
import com.budgetops.backend.budget.dto.BudgetSettingsResponse;
import com.budgetops.backend.budget.dto.BudgetUsageResponse;
import com.budgetops.backend.budget.entity.MemberAccountBudget;
import com.budgetops.backend.budget.repository.MemberAccountBudgetRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Budget Service 테스트")
class BudgetServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CloudCostAggregator cloudCostAggregator;

    @Mock
    private MemberAccountBudgetRepository memberAccountBudgetRepository;

    @InjectMocks
    private BudgetService budgetService;

    private Member testMember;
    private MemberAccountBudget testAccountBudget;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);
        testMember.setEmail("test@example.com");
        testMember.setName("테스트 사용자");
        testMember.setMonthlyBudgetLimit(new BigDecimal("1000000.00"));
        testMember.setBudgetAlertThreshold(80);
        testMember.setUpdatedAt(LocalDateTime.now());

        testAccountBudget = MemberAccountBudget.builder()
                .id(1L)
                .member(testMember)
                .provider("AWS")
                .accountId(100L)
                .monthlyBudgetLimit(new BigDecimal("500000.00"))
                .alertThreshold(80)
                .build();
    }

    @Test
    @DisplayName("getSettings - 예산 설정 조회 성공")
    void getSettings_Success() {
        // given
        List<MemberAccountBudget> accountBudgets = List.of(testAccountBudget);
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(accountBudgets);

        // when
        BudgetSettingsResponse response = budgetService.getSettings(1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.monthlyBudgetLimit()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(response.alertThreshold()).isEqualTo(80);
        assertThat(response.accountBudgets()).hasSize(1);
        assertThat(response.accountBudgets().get(0).provider()).isEqualTo("AWS");
        assertThat(response.accountBudgets().get(0).accountId()).isEqualTo(100L);
        verify(memberRepository).findById(1L);
        verify(memberAccountBudgetRepository).findByMemberId(1L);
    }

    @Test
    @DisplayName("getSettings - 존재하지 않는 회원")
    void getSettings_MemberNotFound() {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> budgetService.getSettings(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member not found");
    }

    @Test
    @DisplayName("updateSettings - 예산 설정 업데이트 성공")
    void updateSettings_Success() {
        // given
        BudgetSettingsRequest request = new BudgetSettingsRequest(
                new BigDecimal("2000000.00"),
                90,
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        BudgetSettingsResponse response = budgetService.updateSettings(1L, request);

        // then
        assertThat(response).isNotNull();
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getMonthlyBudgetLimit()).isEqualTo(new BigDecimal("2000000.00"));
        assertThat(savedMember.getBudgetAlertThreshold()).isEqualTo(90);
        assertThat(savedMember.getBudgetAlertTriggeredAt()).isNull();
        verify(memberAccountBudgetRepository).deleteByMemberId(1L);
    }

    @Test
    @DisplayName("updateSettings - 계정별 예산 설정 저장")
    void updateSettings_WithAccountBudgets() {
        // given
        BudgetSettingsRequest.MemberAccountBudgetSetting accountSetting = 
                new BudgetSettingsRequest.MemberAccountBudgetSetting(
                        "AWS",
                        100L,
                        new BigDecimal("500000.00"),
                        80
                );
        
        BudgetSettingsRequest request = new BudgetSettingsRequest(
                new BigDecimal("2000000.00"),
                90,
                List.of(accountSetting)
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(List.of(testAccountBudget));

        // when
        BudgetSettingsResponse response = budgetService.updateSettings(1L, request);

        // then
        assertThat(response).isNotNull();
        verify(memberAccountBudgetRepository).deleteByMemberId(1L);
        verify(memberAccountBudgetRepository).save(any(MemberAccountBudget.class));
    }

    @Test
    @DisplayName("updateSettings - 0 이하 예산은 저장하지 않음")
    void updateSettings_SkipZeroOrNegativeBudget() {
        // given
        BudgetSettingsRequest.MemberAccountBudgetSetting invalidSetting = 
                new BudgetSettingsRequest.MemberAccountBudgetSetting(
                        "AWS",
                        100L,
                        new BigDecimal("-100.00"),
                        80
                );
        
        BudgetSettingsRequest request = new BudgetSettingsRequest(
                new BigDecimal("1000000.00"),
                80,
                List.of(invalidSetting)
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        budgetService.updateSettings(1L, request);

        // then
        verify(memberAccountBudgetRepository).deleteByMemberId(1L);
        verify(memberAccountBudgetRepository, never()).save(any(MemberAccountBudget.class));
    }

    @Test
    @DisplayName("getUsage - 예산 사용량 조회 성공")
    void getUsage_Success() {
        // given
        CloudCostAggregator.CloudCostSnapshot snapshot = new CloudCostAggregator.CloudCostSnapshot(
                new BigDecimal("800000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "202412",
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(cloudCostAggregator.calculateCurrentMonth(1L)).willReturn(snapshot);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        BudgetUsageResponse response = budgetService.getUsage(1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.monthlyBudgetLimit()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(response.currentMonthCost()).isEqualTo(new BigDecimal("800000.00"));
        assertThat(response.usagePercentage()).isEqualTo(80.0);
        assertThat(response.alertThreshold()).isEqualTo(80);
        assertThat(response.thresholdReached()).isTrue();
        assertThat(response.month()).isEqualTo("202412");
        assertThat(response.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("getUsage - 예산이 0일 때")
    void getUsage_ZeroBudget() {
        // given
        testMember.setMonthlyBudgetLimit(BigDecimal.ZERO);
        CloudCostAggregator.CloudCostSnapshot snapshot = new CloudCostAggregator.CloudCostSnapshot(
                new BigDecimal("100000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "202412",
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(cloudCostAggregator.calculateCurrentMonth(1L)).willReturn(snapshot);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        BudgetUsageResponse response = budgetService.getUsage(1L);

        // then
        assertThat(response.usagePercentage()).isEqualTo(0.0);
        assertThat(response.thresholdReached()).isFalse();
    }

    @Test
    @DisplayName("checkAlerts - 통합 예산 임계값 도달 시 알림 생성")
    void checkAlerts_ConsolidatedBudgetAlert() {
        // given
        CloudCostAggregator.CloudCostSnapshot snapshot = new CloudCostAggregator.CloudCostSnapshot(
                new BigDecimal("850000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "202412",
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(cloudCostAggregator.calculateCurrentMonth(1L)).willReturn(snapshot);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());
        given(memberRepository.save(any(Member.class))).willReturn(testMember);

        // when
        List<BudgetAlertResponse> alerts = budgetService.checkAlerts(1L);

        // then
        assertThat(alerts).hasSize(1);
        BudgetAlertResponse alert = alerts.get(0);
        assertThat(alert.mode()).isEqualTo("CONSOLIDATED");
        assertThat(alert.provider()).isNull();
        assertThat(alert.accountId()).isNull();
        assertThat(alert.usagePercentage()).isGreaterThanOrEqualTo(80.0);
        
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("checkAlerts - 이미 이번 달에 알림이 발송된 경우")
    void checkAlerts_AlreadyTriggeredThisMonth() {
        // given
        testMember.setBudgetAlertTriggeredAt(LocalDateTime.now());
        CloudCostAggregator.CloudCostSnapshot snapshot = new CloudCostAggregator.CloudCostSnapshot(
                new BigDecimal("850000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")),
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(cloudCostAggregator.calculateCurrentMonth(1L)).willReturn(snapshot);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        List<BudgetAlertResponse> alerts = budgetService.checkAlerts(1L);

        // then
        assertThat(alerts).isEmpty();
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("checkAlerts - 임계값 미도달 시 알림 없음")
    void checkAlerts_ThresholdNotReached() {
        // given
        CloudCostAggregator.CloudCostSnapshot snapshot = new CloudCostAggregator.CloudCostSnapshot(
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "202412",
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(cloudCostAggregator.calculateCurrentMonth(1L)).willReturn(snapshot);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        List<BudgetAlertResponse> alerts = budgetService.checkAlerts(1L);

        // then
        assertThat(alerts).isEmpty();
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("checkAlerts - 계정별 예산 임계값 도달 시 알림 생성")
    void checkAlerts_AccountSpecificAlert() {
        // given
        CloudCostAggregator.AccountCostSnapshot accountCost = new CloudCostAggregator.AccountCostSnapshot(
                "AWS",
                100L,
                "Test Account",
                new BigDecimal("450000.00")
        );
        
        CloudCostAggregator.CloudCostSnapshot snapshot = new CloudCostAggregator.CloudCostSnapshot(
                new BigDecimal("450000.00"),
                new BigDecimal("450000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "202412",
                List.of(accountCost)
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(cloudCostAggregator.calculateCurrentMonth(1L)).willReturn(snapshot);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(List.of(testAccountBudget));
        given(memberAccountBudgetRepository.saveAll(any())).willReturn(List.of(testAccountBudget));

        // when
        List<BudgetAlertResponse> alerts = budgetService.checkAlerts(1L);

        // then
        assertThat(alerts).hasSizeGreaterThanOrEqualTo(1);
        boolean hasAccountAlert = alerts.stream()
                .anyMatch(alert -> "ACCOUNT_SPECIFIC".equals(alert.mode()));
        assertThat(hasAccountAlert).isTrue();
    }

    @Test
    @DisplayName("getSettings - 계정별 예산 설정이 없는 경우")
    void getSettings_NoAccountBudgets() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        BudgetSettingsResponse response = budgetService.getSettings(1L);

        // then
        assertThat(response.accountBudgets()).isEmpty();
    }

    @Test
    @DisplayName("updateSettings - 예산 설정 변경 시 알림 재활성화")
    void updateSettings_ResetsAlertTrigger() {
        // given
        testMember.setBudgetAlertTriggeredAt(LocalDateTime.now().minusDays(5));
        BudgetSettingsRequest request = new BudgetSettingsRequest(
                new BigDecimal("1500000.00"),
                85,
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        budgetService.updateSettings(1L, request);

        // then
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getBudgetAlertTriggeredAt()).isNull();
    }

    @Test
    @DisplayName("getUsage - 예산이 null인 경우 0으로 처리")
    void getUsage_NullBudget() {
        // given
        testMember.setMonthlyBudgetLimit(null);
        CloudCostAggregator.CloudCostSnapshot snapshot = new CloudCostAggregator.CloudCostSnapshot(
                new BigDecimal("100000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "202412",
                Collections.emptyList()
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(cloudCostAggregator.calculateCurrentMonth(1L)).willReturn(snapshot);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        BudgetUsageResponse response = budgetService.getUsage(1L);

        // then
        assertThat(response.monthlyBudgetLimit()).isEqualTo(new BigDecimal("0.00"));
        assertThat(response.usagePercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("updateSettings - null 계정별 예산 리스트 처리")
    void updateSettings_NullAccountBudgets() {
        // given
        BudgetSettingsRequest request = new BudgetSettingsRequest(
                new BigDecimal("1000000.00"),
                80,
                null
        );
        
        given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
        given(memberRepository.save(any(Member.class))).willReturn(testMember);
        given(memberAccountBudgetRepository.findByMemberId(1L)).willReturn(Collections.emptyList());

        // when
        BudgetSettingsResponse response = budgetService.updateSettings(1L, request);

        // then
        assertThat(response).isNotNull();
        verify(memberAccountBudgetRepository).deleteByMemberId(1L);
        verify(memberAccountBudgetRepository, never()).save(any(MemberAccountBudget.class));
    }
}

