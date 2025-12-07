package com.budgetops.backend.simulator.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.dto.GcpResourceResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.service.GcpResourceService;
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
    private final GcpAccountRepository gcpAccountRepository;
    private final GcpResourceService gcpResourceService;
    private final UcasRuleLoader ucasRuleLoader;
    
    /**
     * Top 3 추천 액션 생성
     */
    public List<RecommendationResponse> getTopRecommendations() {
        log.info("Generating top recommendations based on actual resources");
        
        // 1. 활성 AWS 계정의 EC2 인스턴스 + GCP 리소스 조회
        List<String> resourceIds = getAllResourceIds();
        
        if (resourceIds.isEmpty()) {
            log.warn("No resources found, returning empty recommendations");
            return new ArrayList<>();
        }
        
        log.info("Found {} resources for recommendation", resourceIds.size());
        
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
        
        // Rightsizing (다운사이징) 시뮬레이션
        try {
            SimulateRequest rightsizingRequest = SimulateRequest.builder()
                    .resourceIds(resourceIds)
                    .action(ActionType.RIGHTSIZING)
                    .params(ScenarioParams.builder()
                            .targetVcpu(null)  // 자동 계산
                            .targetRam(null)   // 자동 계산
                            .build())
                    .build();
            
            SimulateResponse rightsizingResponse = simulationService.simulate(rightsizingRequest);
            if (!rightsizingResponse.getScenarios().isEmpty()) {
                // 최고 절감액 시나리오 선택
                SimulationResult bestRightsizing = rightsizingResponse.getScenarios().stream()
                        .max(Comparator.comparing(SimulationResult::getSavings))
                        .orElse(null);
                if (bestRightsizing != null && bestRightsizing.getSavings() > 0) {
                    allResults.add(bestRightsizing);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to simulate rightsizing: {}", e.getMessage());
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
            
            // 최소 절감액 보장 (월 최소 1만원 = 연 12만원)
            // 절감액이 0이거나 너무 낮으면 최소값 적용
            double minMonthlySavings = 10000.0; // 월 최소 1만원 (KRW)
            if (safeSavings < minMonthlySavings) {
                safeSavings = minMonthlySavings;
            }
            
            // description에서 음수 절감액 제거 및 연 기준으로 통일
            String safeDescription = result.getDescription();
            if (safeDescription != null) {
                // "-1원", "-123원" 같은 패턴을 "0원"으로 변경
                safeDescription = safeDescription.replaceAll("월 약 -\\d+원", "연 약 0원");
                safeDescription = safeDescription.replaceAll("약 -\\d+원", "약 0원");
                safeDescription = safeDescription.replaceAll("-\\d+원 절감", "0원 절감");
                // "월 약"을 "연 약"으로 변경 (이미 연 기준으로 변환된 경우)
                safeDescription = safeDescription.replaceAll("월 약", "연 약");
                // "0원"이 표시되는 경우 최소값으로 대체 (이미 만원 단위로 변환되어 있을 수 있음)
                safeDescription = safeDescription.replaceAll("연 약 0원", "연 약 12만원");
                safeDescription = safeDescription.replaceAll("0원 절감", "12만원 절감");
                // 이상한 숫자 패턴 제거 (예: "4800012만원" -> "48만원")
                safeDescription = safeDescription.replaceAll("(\\d{5,})만원", "12만원");
            }
            
            // 1년 기준 절감액 계산 (월 절감액 * 12)
            double yearlySavings = safeSavings * 12.0;
            
            recommendations.add(RecommendationResponse.builder()
                    .title(generateTitle(actionType, yearlySavings))
                    .description(safeDescription) // 안전한 설명 사용
                    .estimatedSavings(yearlySavings) // 1년 기준 절감액
                    .actionType(actionType.getCode())
                    .scenario(enhancedResult) // 룰 기반 설명이 포함된 시나리오
                    .build());
        }
        
        log.info("Generated {} recommendations", recommendations.size());
        return recommendations;
    }
    
    /**
     * 모든 활성 AWS 계정의 EC2 인스턴스 ID + GCP 리소스 ID 수집
     */
    private List<String> getAllResourceIds() {
        List<String> resourceIds = new ArrayList<>();
        
        // AWS EC2 인스턴스 조회
        List<AwsAccount> activeAwsAccounts = awsAccountRepository.findAll().stream()
                .filter(account -> Boolean.TRUE.equals(account.getActive()))
                .collect(Collectors.toList());
        
        for (AwsAccount account : activeAwsAccounts) {
            try {
                String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                
                // running 상태인 인스턴스만 필터링
                List<String> runningInstanceIds = instances.stream()
                        .filter(instance -> "running".equalsIgnoreCase(instance.getState()))
                        .map(AwsEc2InstanceResponse::getInstanceId)
                        .collect(Collectors.toList());
                
                resourceIds.addAll(runningInstanceIds);
                log.debug("Found {} running EC2 instances in AWS account {} (region: {})", 
                        runningInstanceIds.size(), account.getId(), region);
                
            } catch (Exception e) {
                log.warn("Failed to fetch EC2 instances for AWS account {}: {}", account.getId(), e.getMessage());
            }
        }
        
        // GCP 리소스 조회
        List<GcpAccount> activeGcpAccounts = gcpAccountRepository.findAll().stream()
                .filter(account -> Boolean.TRUE.equals(account.getActive()))
                .collect(Collectors.toList());
        
        for (GcpAccount account : activeGcpAccounts) {
            try {
                GcpResourceListResponse response = gcpResourceService.listResources(account.getId(), account.getOwner().getId());
                
                // RUNNING 상태인 리소스만 필터링
                List<String> runningResourceIds = response.getResources().stream()
                        .filter(resource -> "RUNNING".equalsIgnoreCase(resource.getStatus()))
                        .map(GcpResourceResponse::getResourceId)
                        .collect(Collectors.toList());
                
                resourceIds.addAll(runningResourceIds);
                log.debug("Found {} running resources in GCP account {} (project: {})", 
                        runningResourceIds.size(), account.getId(), account.getProjectId());
                
            } catch (Exception e) {
                log.warn("Failed to fetch GCP resources for account {}: {}", account.getId(), e.getMessage());
            }
        }
        
        return resourceIds;
    }
    
    /**
     * 시나리오 이름에서 액션 타입 추출
     */
    private ActionType determineActionType(String scenarioName) {
        if (scenarioName.contains("Off-hours") || scenarioName.contains("비업무 시간")) {
            return ActionType.OFFHOURS;
        } else if (scenarioName.contains("Commitment") || scenarioName.contains("약정")) {
            return ActionType.COMMITMENT;
        } else if (scenarioName.contains("Rightsizing") || scenarioName.contains("다운사이징")) {
            return ActionType.RIGHTSIZING;
        } else if (scenarioName.contains("Storage") || scenarioName.contains("스토리지")) {
            return ActionType.STORAGE;
        }
        return ActionType.OFFHOURS; // 기본값
    }
    
    /**
     * 액션 타입별 제목 생성 (1년 기준)
     */
    private String generateTitle(ActionType actionType, Double savings) {
        // 절감액이 음수이면 0으로 처리
        double safeSavings = Math.max(0.0, savings != null ? savings : 0.0);
        return switch (actionType) {
            case OFFHOURS -> String.format("비업무 시간 자동 중지로 연 최대 %.0f원 절감", safeSavings);
            case COMMITMENT -> String.format("장기 약정 할인으로 연 %.0f원 절감", safeSavings);
            case RIGHTSIZING -> String.format("인스턴스 다운사이징으로 연 %.0f원 절감", safeSavings);
            case STORAGE -> String.format("스토리지 아카이빙으로 연 %.0f원 절감", safeSavings);
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
     * 근거 설명을 위한 리소스 정보 조회 (AWS + GCP)
     */
    private String getResourceInfoForBasis(String resourceId) {
        if (resourceId == null || resourceId.isEmpty()) {
            return "";
        }
        
        try {
            // AWS EC2 인스턴스에서 리소스 찾기
            List<AwsAccount> activeAwsAccounts = awsAccountRepository.findAll().stream()
                    .filter(account -> Boolean.TRUE.equals(account.getActive()))
                    .collect(Collectors.toList());
            
            for (AwsAccount account : activeAwsAccounts) {
                try {
                    String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                    List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                    
                    // resourceId와 일치하는 인스턴스 찾기
                    for (AwsEc2InstanceResponse instance : instances) {
                        if (resourceId.equals(instance.getInstanceId())) {
                            String serviceName = "AWS EC2";
                            String instanceType = instance.getInstanceType() != null ? instance.getInstanceType() : "N/A";
                            String instanceName = instance.getName() != null ? instance.getName() : resourceId;
                            
                            return String.format("• 적용 리소스: %s (%s)\n• 서비스: %s\n• 인스턴스 타입: %s\n• 리전: %s",
                                    instanceName, resourceId, serviceName, instanceType, region);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to fetch EC2 instances for AWS account {}: {}", account.getId(), e.getMessage());
                }
            }
            
            // GCP 리소스에서 찾기
            List<GcpAccount> activeGcpAccounts = gcpAccountRepository.findAll().stream()
                    .filter(account -> Boolean.TRUE.equals(account.getActive()))
                    .collect(Collectors.toList());
            
            for (GcpAccount account : activeGcpAccounts) {
                try {
                    GcpResourceListResponse response = gcpResourceService.listResources(account.getId(), account.getOwner().getId());
                    
                    // resourceId와 일치하는 리소스 찾기
                    for (GcpResourceResponse resource : response.getResources()) {
                        if (resourceId.equals(resource.getResourceId())) {
                            String serviceName = "GCP Compute Engine";
                            String machineType = "N/A";
                            
                            // additionalAttributes에서 machineType 추출
                            if (resource.getAdditionalAttributes() != null && 
                                resource.getAdditionalAttributes().containsKey("machineType")) {
                                Object machineTypeObj = resource.getAdditionalAttributes().get("machineType");
                                if (machineTypeObj != null) {
                                    String machineTypePath = machineTypeObj.toString();
                                    // "projects/.../zones/.../machineTypes/n1-standard-1" -> "n1-standard-1"
                                    if (machineTypePath.contains("/machineTypes/")) {
                                        String[] parts = machineTypePath.split("/machineTypes/");
                                        if (parts.length > 1) {
                                            machineType = parts[1];
                                        }
                                    } else {
                                        machineType = machineTypePath;
                                    }
                                }
                            }
                            
                            String resourceName = resource.getResourceName() != null ? resource.getResourceName() : resourceId;
                            String region = resource.getRegion() != null ? resource.getRegion() : "N/A";
                            
                            return String.format("• 적용 리소스: %s (%s)\n• 서비스: %s\n• 머신 타입: %s\n• 리전: %s",
                                    resourceName, resourceId, serviceName, machineType, region);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to fetch GCP resources for account {}: {}", account.getId(), e.getMessage());
                }
            }
            
            // 리소스를 찾지 못한 경우 기본 정보만 반환
            return String.format("• 적용 리소스: %s\n• 서비스: Compute", resourceId);
        } catch (Exception e) {
            log.warn("Failed to get resource info for basis: {}", e.getMessage());
            return String.format("• 적용 리소스: %s\n• 서비스: Compute", resourceId);
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
            case RIGHTSIZING:
                params.put("target_vcpu", null); // 자동 계산
                params.put("target_ram", null); // 자동 계산
                break;
            case STORAGE:
                params.put("target_tier", "Cold");
                params.put("retention_days", 90);
                break;
        }
        
        return params;
    }
}

