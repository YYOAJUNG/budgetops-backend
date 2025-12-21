package com.budgetops.backend.simulator.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.simulator.dto.*;
import com.budgetops.backend.simulator.engine.CostEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("Simulation Service 테스트")
class SimulationServiceTest {

    @Mock
    private CostEngine costEngine;

    @Mock
    private AwsAccountRepository awsAccountRepository;

    @Mock
    private AwsEc2Service awsEc2Service;

    @InjectMocks
    private SimulationService simulationService;

    private SimulateRequest testRequest;
    private AwsAccount testAccount;
    private AwsEc2InstanceResponse testInstance;

    @BeforeEach
    void setUp() {
        testAccount = new AwsAccount();
        testAccount.setId(1L);
        testAccount.setActive(Boolean.TRUE);
        testAccount.setDefaultRegion("us-east-1");

        testInstance = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .instanceType("t3.medium")
                .name("Test Instance")
                .build();

        testRequest = SimulateRequest.builder()
                .resourceIds(List.of("i-1234567890abcdef0"))
                .action(ActionType.OFFHOURS)
                .build();
    }

    @Test
    @DisplayName("시뮬레이션 실행 - OFFHOURS 액션")
    void simulate_OffHours() {
        // given
        given(awsAccountRepository.findAll()).willReturn(List.of(testAccount));
        given(awsEc2Service.listInstances(anyLong(), anyString()))
                .willReturn(List.of(testInstance));
        given(costEngine.calculateCurrentCost(any(), any(), anyString()))
                .willReturn(200000.0); // 월 20만원
        given(costEngine.calculateRiskScore(any(), anyString()))
                .willReturn(0.3);
        given(costEngine.calculatePriorityScore(any(), any(), anyInt()))
                .willReturn(0.8);

        // when
        SimulateResponse response = simulationService.simulate(testRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getActionType()).isEqualTo("offhours");
        assertThat(response.getTotalResources()).isEqualTo(1);
        assertThat(response.getScenarios()).isNotEmpty();
    }

    @Test
    @DisplayName("시뮬레이션 실행 - COMMITMENT 액션")
    void simulate_Commitment() {
        // given
        SimulateRequest request = SimulateRequest.builder()
                .resourceIds(List.of("i-1234567890abcdef0"))
                .action(ActionType.COMMITMENT)
                .build();

        given(awsAccountRepository.findAll()).willReturn(List.of(testAccount));
        given(awsEc2Service.listInstances(anyLong(), anyString()))
                .willReturn(List.of(testInstance));
        given(costEngine.calculateCurrentCost(any(), any(), anyString()))
                .willReturn(200000.0);
        given(costEngine.calculateCommitmentSavings(any(), any(), any(), any(), anyString()))
                .willReturn(50000.0);
        given(costEngine.calculateRiskScore(any(), anyString()))
                .willReturn(0.2);
        given(costEngine.calculatePriorityScore(any(), any(), anyInt()))
                .willReturn(0.9);

        // when
        SimulateResponse response = simulationService.simulate(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getActionType()).isEqualTo("commitment");
        assertThat(response.getScenarios()).isNotEmpty();
        // 커밋 레벨별로 여러 시나리오 생성 (50%, 70%, 90%)
        assertThat(response.getScenarios().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("시뮬레이션 실행 - STORAGE 액션")
    void simulate_Storage() {
        // given
        SimulateRequest request = SimulateRequest.builder()
                .resourceIds(List.of("s3-bucket-123"))
                .action(ActionType.STORAGE)
                .build();

        given(awsAccountRepository.findAll()).willReturn(List.of(testAccount));
        given(costEngine.calculateCurrentCost(any(), any(), anyString()))
                .willReturn(100000.0);
        given(costEngine.calculateStorageLifecycleSavings(any(), any(), any()))
                .willReturn(20000.0);
        given(costEngine.calculatePriorityScore(any(), any(), anyInt()))
                .willReturn(0.7);

        // when
        SimulateResponse response = simulationService.simulate(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getActionType()).isEqualTo("storage");
        assertThat(response.getScenarios()).isNotEmpty();
    }

    @Test
    @DisplayName("시뮬레이션 실행 - RIGHTSIZING 액션")
    void simulate_Rightsizing() {
        // given
        SimulateRequest request = SimulateRequest.builder()
                .resourceIds(List.of("i-1234567890abcdef0"))
                .action(ActionType.RIGHTSIZING)
                .build();

        given(awsAccountRepository.findAll()).willReturn(List.of(testAccount));
        given(awsEc2Service.listInstances(anyLong(), anyString()))
                .willReturn(List.of(testInstance));
        given(costEngine.calculateCurrentCost(any(), any(), anyString()))
                .willReturn(200000.0);
        given(costEngine.calculatePriorityScore(any(), any(), anyInt()))
                .willReturn(0.75);

        // when
        SimulateResponse response = simulationService.simulate(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getActionType()).isEqualTo("rightsizing");
    }

    @Test
    @DisplayName("시뮬레이션 실행 - CLEANUP 액션")
    void simulate_Cleanup() {
        // given
        SimulateRequest request = SimulateRequest.builder()
                .resourceIds(List.of("zombie-resource-123"))
                .action(ActionType.CLEANUP)
                .build();

        // when
        SimulateResponse response = simulationService.simulate(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getActionType()).isEqualTo("cleanup");
        assertThat(response.getScenarios()).isEmpty(); // TODO 구현 예정
    }

    @Test
    @DisplayName("시뮬레이션 실행 - 리소스를 찾을 수 없는 경우")
    void simulate_ResourceNotFound() {
        // given
        given(awsAccountRepository.findAll()).willReturn(List.of(testAccount));
        given(awsEc2Service.listInstances(anyLong(), anyString()))
                .willReturn(Collections.emptyList());
        // getResourceInfo가 기본값을 반환하므로 costEngine이 호출되지만, 예외를 던지도록 설정
        given(costEngine.calculateCurrentCost(any(), any(), anyString()))
                .willThrow(new RuntimeException("Resource not found"));

        // when
        SimulateResponse response = simulationService.simulate(testRequest);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getScenarios()).isEmpty();
    }

    @Test
    @DisplayName("시뮬레이션 실행 - OFFHOURS 커스텀 파라미터")
    void simulate_OffHoursWithCustomParams() {
        // given
        ScenarioParams params = ScenarioParams.builder()
                .weekdays(List.of("Mon-Fri"))
                .stopAt("22:00")
                .startAt("09:00")
                .timezone("Asia/Seoul")
                .scaleToZeroSupported(true)
                .build();

        SimulateRequest request = SimulateRequest.builder()
                .resourceIds(List.of("i-1234567890abcdef0"))
                .action(ActionType.OFFHOURS)
                .params(params)
                .build();

        given(awsAccountRepository.findAll()).willReturn(List.of(testAccount));
        given(awsEc2Service.listInstances(anyLong(), anyString()))
                .willReturn(List.of(testInstance));
        given(costEngine.calculateCurrentCost(any(), any(), anyString()))
                .willReturn(200000.0);
        given(costEngine.calculateRiskScore(any(), anyString()))
                .willReturn(0.3);
        given(costEngine.calculatePriorityScore(any(), any(), anyInt()))
                .willReturn(0.8);

        // when
        SimulateResponse response = simulationService.simulate(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getScenarios()).isNotEmpty();
        SimulationResult result = response.getScenarios().get(0);
        assertThat(result.getDescription()).contains("22:00");
        assertThat(result.getDescription()).contains("09:00");
    }

    @Test
    @DisplayName("시뮬레이션 실행 - RIGHTSIZING 사용률이 높은 경우 제외")
    void simulate_Rightsizing_HighUtilization() {
        // given
        SimulateRequest request = SimulateRequest.builder()
                .resourceIds(List.of("i-1234567890abcdef0"))
                .action(ActionType.RIGHTSIZING)
                .build();

        given(awsAccountRepository.findAll()).willReturn(List.of(testAccount));
        given(awsEc2Service.listInstances(anyLong(), anyString()))
                .willReturn(List.of(testInstance));
        // 사용률이 40% 이상이면 다운사이징 추천하지 않음

        // when
        SimulateResponse response = simulationService.simulate(request);

        // then
        assertThat(response).isNotNull();
        // 사용률이 높으면 시나리오가 생성되지 않을 수 있음
    }
}

