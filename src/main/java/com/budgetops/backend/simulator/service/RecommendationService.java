package com.budgetops.backend.simulator.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.simulator.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 추천 액션 서비스
 * 실제 리소스 데이터를 기반으로 Top 3 추천 액션 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    
    private final SimulationService simulationService;
    private final AwsAccountRepository awsAccountRepository;
    private final AwsEc2Service awsEc2Service;
    
    /**
     * Top 3 추천 액션 생성
     */
    public List<RecommendationResponse> getTopRecommendations() {
        log.info("Generating top recommendations based on actual resources");
        
        // 1. 활성 AWS 계정의 EC2 인스턴스 조회
        List<String> resourceIds = getAllEc2InstanceIds();
        
        if (resourceIds.isEmpty()) {
            log.warn("No EC2 instances found, returning empty recommendations");
            return new ArrayList<>();
        }
        
        log.info("Found {} EC2 instances for recommendation", resourceIds.size());
        
        // 2. 각 액션 타입별로 시뮬레이션 실행
        List<SimulationResult> allResults = new ArrayList<>();
        
        // Off-hours 시뮬레이션
        try {
            SimulateRequest offHoursRequest = SimulateRequest.builder()
                    .resourceIds(resourceIds)
                    .action(ActionType.OFFHOURS)
                    .params(ScenarioParams.builder()
                            .weekdays(List.of("Mon-Fri"))
                            .stopAt("20:00")
                            .startAt("08:30")
                            .timezone("Asia/Seoul")
                            .scaleToZeroSupported(true)
                            .build())
                    .build();
            
            SimulateResponse offHoursResponse = simulationService.simulate(offHoursRequest);
            if (!offHoursResponse.getScenarios().isEmpty()) {
                // 각 리소스별 최고 절감액 시나리오만 선택
                SimulationResult bestOffHours = offHoursResponse.getScenarios().stream()
                        .max(Comparator.comparing(SimulationResult::getSavings))
                        .orElse(null);
                if (bestOffHours != null) {
                    allResults.add(bestOffHours);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to simulate off-hours: {}", e.getMessage());
        }
        
        // Commitment 시뮬레이션
        try {
            SimulateRequest commitmentRequest = SimulateRequest.builder()
                    .resourceIds(resourceIds)
                    .action(ActionType.COMMITMENT)
                    .params(ScenarioParams.builder()
                            .commitLevel(0.7)
                            .commitYears(1)
                            .build())
                    .build();
            
            SimulateResponse commitmentResponse = simulationService.simulate(commitmentRequest);
            if (!commitmentResponse.getScenarios().isEmpty()) {
                // 70% 커밋 시나리오 중 최고 절감액 선택
                SimulationResult bestCommitment = commitmentResponse.getScenarios().stream()
                        .filter(s -> s.getScenarioName().contains("70%"))
                        .max(Comparator.comparing(SimulationResult::getSavings))
                        .orElse(null);
                if (bestCommitment != null) {
                    allResults.add(bestCommitment);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to simulate commitment: {}", e.getMessage());
        }
        
        // Storage 시뮬레이션 (현재는 EC2 기반이므로 스킵하거나 기본값 사용)
        // TODO: 실제 스토리지 리소스 조회 후 시뮬레이션
        
        // 3. 우선순위 점수로 정렬하여 Top 3 선택
        List<SimulationResult> topResults = allResults.stream()
                .sorted(Comparator.comparing(SimulationResult::getPriorityScore).reversed())
                .limit(3)
                .collect(Collectors.toList());
        
        // 4. RecommendationResponse로 변환
        List<RecommendationResponse> recommendations = new ArrayList<>();
        for (SimulationResult result : topResults) {
            ActionType actionType = determineActionType(result.getScenarioName());
            recommendations.add(RecommendationResponse.builder()
                    .title(generateTitle(actionType, result.getSavings()))
                    .description(result.getDescription())
                    .estimatedSavings(result.getSavings())
                    .actionType(actionType.getCode())
                    .scenario(result)
                    .build());
        }
        
        log.info("Generated {} recommendations", recommendations.size());
        return recommendations;
    }
    
    /**
     * 모든 활성 AWS 계정의 EC2 인스턴스 ID 수집
     */
    private List<String> getAllEc2InstanceIds() {
        List<String> instanceIds = new ArrayList<>();
        
        List<AwsAccount> activeAccounts = awsAccountRepository.findAll().stream()
                .filter(account -> Boolean.TRUE.equals(account.getActive()))
                .collect(Collectors.toList());
        
        for (AwsAccount account : activeAccounts) {
            try {
                String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                
                // running 상태인 인스턴스만 필터링
                List<String> runningInstanceIds = instances.stream()
                        .filter(instance -> "running".equalsIgnoreCase(instance.getState()))
                        .map(AwsEc2InstanceResponse::getInstanceId)
                        .collect(Collectors.toList());
                
                instanceIds.addAll(runningInstanceIds);
                log.debug("Found {} running instances in account {} (region: {})", 
                        runningInstanceIds.size(), account.getId(), region);
                
            } catch (Exception e) {
                log.warn("Failed to fetch EC2 instances for account {}: {}", account.getId(), e.getMessage());
            }
        }
        
        return instanceIds;
    }
    
    /**
     * 시나리오 이름에서 액션 타입 추출
     */
    private ActionType determineActionType(String scenarioName) {
        if (scenarioName.contains("Off-hours")) {
            return ActionType.OFFHOURS;
        } else if (scenarioName.contains("Commitment")) {
            return ActionType.COMMITMENT;
        } else if (scenarioName.contains("Storage")) {
            return ActionType.STORAGE;
        }
        return ActionType.OFFHOURS; // 기본값
    }
    
    /**
     * 액션 타입별 제목 생성
     */
    private String generateTitle(ActionType actionType, Double savings) {
        return switch (actionType) {
            case OFFHOURS -> String.format("Off-hours로 월 최대 %.0f원 절감 예상", savings);
            case COMMITMENT -> String.format("커밋 70%%로 전환 시 %.0f원 절감", savings);
            case STORAGE -> String.format("90일 미접근 스토리지 아카이빙으로 %.0f원 절감", savings);
            default -> "비용 최적화 추천";
        };
    }
}

