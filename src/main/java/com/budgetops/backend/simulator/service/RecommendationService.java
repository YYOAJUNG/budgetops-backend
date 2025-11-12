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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final UcasRuleLoader ucasRuleLoader;
    
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
        
        // 4. RecommendationResponse로 변환 (UCAS 룰 기반 근거 포함)
        List<RecommendationResponse> recommendations = new ArrayList<>();
        for (SimulationResult result : topResults) {
            ActionType actionType = determineActionType(result.getScenarioName());
            
            // UCAS 룰 기반 상세 근거 생성
            String basisDescription = generateBasisWithRule(actionType, result);
            
            // 시나리오 결과에 룰 기반 설명 추가
            SimulationResult enhancedResult = SimulationResult.builder()
                    .scenarioName(result.getScenarioName())
                    .newCost(result.getNewCost())
                    .currentCost(result.getCurrentCost())
                    .savings(result.getSavings())
                    .riskScore(result.getRiskScore())
                    .priorityScore(result.getPriorityScore())
                    .confidence(result.getConfidence())
                    .yamlPatch(result.getYamlPatch())
                    .description(basisDescription) // 룰 기반 상세 설명으로 교체
                    .build();
            
            // 절감액이 음수인 경우 0으로 처리
            double safeSavings = Math.max(0.0, result.getSavings());
            
            // description에서 음수 절감액 제거
            String safeDescription = result.getDescription();
            if (safeDescription != null) {
                // "-1원", "-123원" 같은 패턴을 "0원"으로 변경
                safeDescription = safeDescription.replaceAll("월 약 -\\d+원", "월 약 0원");
                safeDescription = safeDescription.replaceAll("약 -\\d+원", "약 0원");
                safeDescription = safeDescription.replaceAll("-\\d+원 절감", "0원 절감");
            }
            
            recommendations.add(RecommendationResponse.builder()
                    .title(generateTitle(actionType, safeSavings))
                    .description(safeDescription) // 안전한 설명 사용
                    .estimatedSavings(safeSavings)
                    .actionType(actionType.getCode())
                    .scenario(enhancedResult) // 룰 기반 설명이 포함된 시나리오
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
        // 절감액이 음수이면 0으로 처리
        double safeSavings = Math.max(0.0, savings != null ? savings : 0.0);
        return switch (actionType) {
            case OFFHOURS -> String.format("Off-hours로 월 최대 %.0f원 절감 예상", safeSavings);
            case COMMITMENT -> String.format("커밋 70%%로 전환 시 %.0f원 절감", safeSavings);
            case STORAGE -> String.format("90일 미접근 스토리지 아카이빙으로 %.0f원 절감", safeSavings);
            default -> "비용 최적화 추천";
        };
    }
    
    /**
     * UCAS 룰 기반 상세 근거 생성 (리소스 정보 포함)
     */
    private String generateBasisWithRule(ActionType actionType, SimulationResult result) {
        // 액션 타입에 맞는 파라미터 추출
        Map<String, Object> params = extractParamsFromResult(actionType, result);
        
        // 시나리오 이름에서 resourceId 추출
        String resourceId = extractResourceIdFromScenarioName(result.getScenarioName());
        
        // 리소스 정보 조회 (서비스명, CSP 등)
        String resourceInfo = getResourceInfoForBasis(resourceId);
        
        // UCAS 룰 로더를 통해 근거 생성
        String basis = ucasRuleLoader.generateBasisDescription(actionType.getCode(), result.getSavings(), params);
        
        // 리소스 정보를 앞에 추가
        if (!resourceInfo.isEmpty()) {
            return resourceInfo + "\n\n" + basis;
        }
        return basis;
    }
    
    /**
     * 시나리오 이름에서 resourceId 추출
     */
    private String extractResourceIdFromScenarioName(String scenarioName) {
        if (scenarioName == null) {
            return "";
        }
        // "Off-hours 스케줄링: i-12345" -> "i-12345"
        // "Commitment 최적화 (70%): i-12345" -> "i-12345"
        int lastColonIndex = scenarioName.lastIndexOf(":");
        if (lastColonIndex >= 0 && lastColonIndex < scenarioName.length() - 1) {
            return scenarioName.substring(lastColonIndex + 1).trim();
        }
        return "";
    }
    
    /**
     * 근거 설명을 위한 리소스 정보 조회
     */
    private String getResourceInfoForBasis(String resourceId) {
        if (resourceId == null || resourceId.isEmpty()) {
            return "";
        }
        
        try {
            // 활성 AWS 계정에서 리소스 찾기
            List<AwsAccount> activeAccounts = awsAccountRepository.findAll().stream()
                    .filter(account -> Boolean.TRUE.equals(account.getActive()))
                    .collect(Collectors.toList());
            
            for (AwsAccount account : activeAccounts) {
                try {
                    String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                    List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                    
                    // resourceId와 일치하는 인스턴스 찾기
                    for (AwsEc2InstanceResponse instance : instances) {
                        if (resourceId.equals(instance.getInstanceId())) {
                            // 리소스 정보 반환
                            String serviceName = "EC2";
                            String instanceType = instance.getInstanceType() != null ? instance.getInstanceType() : "N/A";
                            String instanceName = instance.getName() != null ? instance.getName() : resourceId;
                            
                            return String.format("• 적용 리소스: %s (%s)\n• 서비스: %s\n• 인스턴스 타입: %s\n• 리전: %s",
                                    instanceName, resourceId, serviceName, instanceType, region);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to fetch instances for account {}: {}", account.getId(), e.getMessage());
                }
            }
            
            // 리소스를 찾지 못한 경우 기본 정보만 반환
            return String.format("• 적용 리소스: %s\n• 서비스: EC2", resourceId);
        } catch (Exception e) {
            log.warn("Failed to get resource info for basis: {}", e.getMessage());
            return String.format("• 적용 리소스: %s\n• 서비스: EC2", resourceId);
        }
    }
    
    /**
     * 시나리오 결과에서 파라미터 추출
     */
    private Map<String, Object> extractParamsFromResult(ActionType actionType, SimulationResult result) {
        Map<String, Object> params = new HashMap<>();
        
        switch (actionType) {
            case OFFHOURS:
                params.put("stop", Map.of(
                    "weekdays", List.of("Mon-Fri"),
                    "stop_at", "20:00",
                    "start_at", "08:30",
                    "timezone", "Asia/Seoul"
                ));
                break;
            case COMMITMENT:
                params.put("commit_level", 0.7);
                params.put("commit_years", 1);
                break;
            case STORAGE:
                params.put("target_tier", "Cold");
                params.put("retention_days", 90);
                break;
        }
        
        return params;
    }
}

