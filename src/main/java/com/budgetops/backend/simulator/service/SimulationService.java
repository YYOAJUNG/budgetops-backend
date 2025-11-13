package com.budgetops.backend.simulator.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.simulator.dto.*;
import com.budgetops.backend.simulator.engine.CostEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UCAS (Universal Cost Action Simulator) 서비스
 * 모든 CSP/서비스에 공통으로 적용되는 시뮬레이션 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {
    
    private final CostEngine costEngine;
    private final AwsAccountRepository awsAccountRepository;
    private final AwsEc2Service awsEc2Service;
    
    /**
     * 시뮬레이션 실행
     */
    public SimulateResponse simulate(SimulateRequest request) {
        log.info("Starting simulation: action={}, resourceCount={}", 
                request.getAction(), request.getResourceIds().size());
        
        List<SimulationResult> scenarios = new ArrayList<>();
        
        switch (request.getAction()) {
            case OFFHOURS -> scenarios = simulateOffHours(request);
            case COMMITMENT -> scenarios = simulateCommitment(request);
            case STORAGE -> scenarios = simulateStorage(request);
            case RIGHTSIZING -> scenarios = simulateRightsizing(request);
            case CLEANUP -> scenarios = simulateCleanup(request);
        }
        
        return SimulateResponse.builder()
                .scenarios(scenarios)
                .actionType(request.getAction().getCode())
                .totalResources(request.getResourceIds().size())
                .build();
    }
    
    /**
     * Off-hours 스케줄링 시뮬레이션
     * 적용 대상: EC2/GCE/Azure VM, RDS/Cloud SQL/SQL DB(스케일다운), 컨테이너 노드, 개발용 DWH
     */
    private List<SimulationResult> simulateOffHours(SimulateRequest request) {
        List<SimulationResult> results = new ArrayList<>();
        ScenarioParams params = request.getParams();
        
        if (params == null) {
            // 기본값: 주중 20:00~08:30 중단
            params = ScenarioParams.builder()
                    .weekdays(List.of("Mon-Fri"))
                    .stopAt("20:00")
                    .startAt("08:30")
                    .timezone("Asia/Seoul")
                    .scaleToZeroSupported(true)
                    .build();
        }
        
        // 일일 중단 시간 계산
        double dailyOffHours = calculateDailyOffHours(params.getStopAt(), params.getStartAt());
        
        // 각 리소스에 대해 시뮬레이션
        for (String resourceId : request.getResourceIds()) {
            try {
                // 실제 리소스 정보 조회 (예: EC2 인스턴스)
                ResourceInfo resource = getResourceInfo(resourceId);
                PricingInfo pricing = getPricingInfo(resource);
                UsageMetrics metrics = getUsageMetrics(resourceId, resource);
                
                // 태그 기반 필터링 (env=dev, owner 미지정 제외)
                if (shouldExcludeFromOffHours(resource)) {
                    continue;
                }
                
                // 현재 비용 계산 (월간 전체 비용)
                double currentCost = costEngine.calculateCurrentCost(
                        pricing.getUnitPrice(), 1.0, pricing.getUnit());
                
                // 비용이 너무 낮으면 최소값 보장 (월 최소 10만원 = 연 120만원)
                // 실제 인스턴스 비용이 낮을 수 있으므로 최소값 적용
                double minMonthlyCost = 100000.0; // 월 최소 10만원 (KRW)
                if (currentCost < minMonthlyCost) {
                    currentCost = minMonthlyCost;
                }
                
                // Off-hours 절감액 계산
                // 주중(월~금)만 적용하므로 22일 기준
                double monthlyOffHours = dailyOffHours * 22; // 주중 22일
                double savings = (monthlyOffHours / (24.0 * 30.0)) * currentCost;
                
                // 최소 절감액 보장 (월 최소 1만원 = 연 12만원)
                double minMonthlySavings = 10000.0; // 월 최소 1만원 (KRW)
                savings = Math.max(savings, minMonthlySavings);
                
                // 절감액이 현재 비용을 초과하지 않도록 보장
                savings = Math.min(savings, currentCost * 0.5); // 최대 50% 절감
                
                // 변경 후 비용 계산
                double newCost = Math.max(0.0, currentCost - savings);
                
                // 리스크 스코어 계산
                double riskScore = costEngine.calculateRiskScore(metrics, "medium");
                
                // 우선순위 점수 계산 (적용 난이도: 2)
                double priorityScore = costEngine.calculatePriorityScore(savings, riskScore, 2);
                
                // 시나리오 생성
                String scenarioName = String.format("비업무 시간 자동 중지: %s", resourceId);
                // 절감액이 0보다 작으면 0으로 표시
                double displaySavings = Math.max(0.0, savings);
                double yearlySavings = displaySavings * 12.0;
                String description = String.format(
                        "주중 %s~%s 시간대에 자동 중지하여 연 약 %.0f원 절감 예상",
                        params.getStopAt(), params.getStartAt(), yearlySavings);
                
                SimulationResult result = SimulationResult.builder()
                        .scenarioName(scenarioName)
                        .currentCost(currentCost)
                        .newCost(newCost)
                        .savings(savings)
                        .riskScore(riskScore)
                        .priorityScore(priorityScore)
                        .confidence(1.0 - riskScore)
                        .description(description)
                        .build();
                
                results.add(result);
                
            } catch (Exception e) {
                log.warn("Failed to simulate off-hours for resource {}: {}", resourceId, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Commitment 최적화 시뮬레이션
     * 적용 대상: 대부분의 컴퓨트/DWH
     */
    private List<SimulationResult> simulateCommitment(SimulateRequest request) {
        List<SimulationResult> results = new ArrayList<>();
        ScenarioParams params = request.getParams();
        
        if (params == null) {
            // 기본값: 70% 커밋, 1년 약정
            params = ScenarioParams.builder()
                    .commitLevel(0.7)
                    .commitYears(1)
                    .build();
        }
        
        // 커버리지별 시나리오 생성 (50%, 70%, 90%)
        List<Double> commitLevels = List.of(0.5, 0.7, 0.9);
        
        for (String resourceId : request.getResourceIds()) {
            try {
                ResourceInfo resource = getResourceInfo(resourceId);
                PricingInfo pricing = getPricingInfo(resource);
                UsageMetrics metrics = getUsageMetrics(resourceId, resource);
                
                if (!Boolean.TRUE.equals(pricing.getCommitmentApplicable())) {
                    continue;
                }
                
                double currentCost = costEngine.calculateCurrentCost(
                        pricing.getUnitPrice(), 1.0, pricing.getUnit());
                
                // 비용이 너무 낮으면 최소값 보장 (월 최소 10만원)
                double minMonthlyCost = 100000.0; // 월 최소 10만원 (KRW)
                if (currentCost < minMonthlyCost) {
                    currentCost = minMonthlyCost;
                }
                
                for (Double commitLevel : commitLevels) {
                    // 약정 단가 가정 (온디맨드 대비 50% 할인)
                    double commitmentPrice = pricing.getUnitPrice() * 0.5;
                    
                    double savings = costEngine.calculateCommitmentSavings(
                            pricing.getUnitPrice(),
                            commitmentPrice,
                            commitLevel,
                            1.0,
                            pricing.getUnit());
                    
                    // 최소 절감액 보장 (월 최소 1만원)
                    double minMonthlySavings = 10000.0; // 월 최소 1만원 (KRW)
                    savings = Math.max(savings, minMonthlySavings);
                    
                    // 절감액이 현재 비용의 70%를 초과하지 않도록 보장
                    savings = Math.min(savings, currentCost * 0.7);
                    
                    double riskScore = costEngine.calculateRiskScore(metrics, "low");
                    double priorityScore = costEngine.calculatePriorityScore(savings, riskScore, 3);
                    
                    String scenarioName = String.format("장기 약정 할인 (%d%%): %s", 
                            (int)(commitLevel * 100), resourceId);
                    // 절감액이 0보다 작으면 0으로 표시
                    double displaySavings = Math.max(0.0, savings);
                    double yearlySavings = displaySavings * 12.0;
                    String description = String.format(
                            "%d년 장기 약정, %d%% 커버리지로 연 약 %.0f원 절감 예상",
                            params.getCommitYears(), (int)(commitLevel * 100), yearlySavings);
                    
                    SimulationResult result = SimulationResult.builder()
                            .scenarioName(scenarioName)
                            .currentCost(currentCost)
                            .newCost(currentCost - savings)
                            .savings(savings)
                            .riskScore(riskScore)
                            .priorityScore(priorityScore)
                            .confidence(1.0 - riskScore)
                            .description(description)
                            .build();
                    
                    results.add(result);
                }
                
            } catch (Exception e) {
                log.warn("Failed to simulate commitment for resource {}: {}", resourceId, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Storage 수명주기 시뮬레이션
     * 적용 대상: S3/GCS/Azure Blob, EBS/PD/Managed Disk 스냅샷
     */
    private List<SimulationResult> simulateStorage(SimulateRequest request) {
        List<SimulationResult> results = new ArrayList<>();
        ScenarioParams params = request.getParams();
        
        if (params == null) {
            params = ScenarioParams.builder()
                    .targetTier("Cold")
                    .retentionDays(90)
                    .build();
        }
        
        // 정책별 시나리오 (30일, 60일, 90일)
        List<Integer> retentionDays = List.of(30, 60, 90);
        
        for (String resourceId : request.getResourceIds()) {
            try {
                ResourceInfo resource = getResourceInfo(resourceId);
                PricingInfo pricing = getPricingInfo(resource);
                
                // 스토리지 용량 가정 (실제로는 리소스에서 조회)
                double sizeGB = 100.0;  // TODO: 실제 용량 조회
                
                // 현재 스토리지 클래스 단가
                double currentTierPrice = pricing.getUnitPrice();
                
                // 목표 스토리지 클래스 단가 (Cold는 약 50% 저렴)
                double targetTierPrice = currentTierPrice * 0.5;
                
                for (Integer retention : retentionDays) {
                    double savings = costEngine.calculateStorageLifecycleSavings(
                            currentTierPrice, targetTierPrice, sizeGB);
                    
                    double riskScore = 0.2;  // 스토리지 수명주기는 리스크 낮음
                    double priorityScore = costEngine.calculatePriorityScore(savings, riskScore, 1);
                    
                    String scenarioName = String.format("Storage 수명주기 (%d일): %s", retention, resourceId);
                    // 절감액이 0보다 작으면 0으로 표시
                    double displaySavings = Math.max(0.0, savings);
                    double yearlySavings = displaySavings * 12.0;
                    String description = String.format(
                            "%d일 미접근 객체를 %s로 이동하여 연 약 %.0f원 절감 예상",
                            retention, params.getTargetTier(), yearlySavings);
                    
                    SimulationResult result = SimulationResult.builder()
                            .scenarioName(scenarioName)
                            .currentCost(costEngine.calculateCurrentCost(currentTierPrice, sizeGB, "GB-month"))
                            .newCost(costEngine.calculateCurrentCost(targetTierPrice, sizeGB, "GB-month"))
                            .savings(savings)
                            .riskScore(riskScore)
                            .priorityScore(priorityScore)
                            .confidence(0.8)
                            .description(description)
                            .build();
                    
                    results.add(result);
                }
                
            } catch (Exception e) {
                log.warn("Failed to simulate storage for resource {}: {}", resourceId, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Rightsizing (다운사이징) 시뮬레이션
     * 적용 대상: EC2/GCE/Azure VM, RDS/Cloud SQL/SQL DB
     */
    private List<SimulationResult> simulateRightsizing(SimulateRequest request) {
        List<SimulationResult> results = new ArrayList<>();
        
        for (String resourceId : request.getResourceIds()) {
            try {
                ResourceInfo resource = getResourceInfo(resourceId);
                PricingInfo pricing = getPricingInfo(resource);
                UsageMetrics metrics = getUsageMetrics(resourceId, resource);
                
                // 평균 사용률이 40% 미만인 경우만 다운사이징 추천
                double avgUtilization = metrics.getAvg() != null ? metrics.getAvg() : 50.0;
                if (avgUtilization >= 40.0) {
                    continue;
                }
                
                // 현재 비용 계산
                double currentCost = costEngine.calculateCurrentCost(
                        pricing.getUnitPrice(), 1.0, pricing.getUnit());
                
                // 비용이 너무 낮으면 최소값 보장 (월 최소 10만원)
                double minMonthlyCost = 100000.0; // 월 최소 10만원 (KRW)
                if (currentCost < minMonthlyCost) {
                    currentCost = minMonthlyCost;
                }
                
                // 다운사이징 시 약 30-50% 비용 절감 가정 (인스턴스 타입에 따라 다름)
                // 사용률이 낮을수록 더 많은 절감 가능
                double savingsRate = Math.min(0.5, 0.3 + (40.0 - avgUtilization) / 100.0); // 30-50% 절감
                double savings = currentCost * savingsRate;
                
                // 최소 절감액 보장 (월 최소 1만원)
                double minMonthlySavings = 10000.0; // 월 최소 1만원 (KRW)
                savings = Math.max(savings, minMonthlySavings);
                
                // 절감액이 현재 비용의 50%를 초과하지 않도록 보장
                savings = Math.min(savings, currentCost * 0.5);
                
                // 변경 후 비용 계산
                double newCost = Math.max(0.0, currentCost - savings);
                
                // 리스크 스코어 계산 (다운사이징은 중간 리스크)
                double riskScore = 0.3; // 중간 리스크
                
                // 우선순위 점수 계산 (적용 난이도: 3)
                double priorityScore = costEngine.calculatePriorityScore(savings, riskScore, 3);
                
                // 시나리오 생성
                String scenarioName = String.format("다운사이징: %s", resourceId);
                double displaySavings = Math.max(0.0, savings);
                double yearlySavings = displaySavings * 12.0;
                String description = String.format(
                        "CPU 및 메모리 사용률이 평균 %.1f%%로 낮아 한 단계 작은 인스턴스 타입으로 변경 시 연 약 %.0f원 절감 예상",
                        avgUtilization, yearlySavings);
                
                SimulationResult result = SimulationResult.builder()
                        .scenarioName(scenarioName)
                        .currentCost(currentCost)
                        .newCost(newCost)
                        .savings(savings)
                        .riskScore(riskScore)
                        .priorityScore(priorityScore)
                        .confidence(1.0 - riskScore)
                        .description(description)
                        .build();
                
                results.add(result);
                
            } catch (Exception e) {
                log.warn("Failed to simulate rightsizing for resource {}: {}", resourceId, e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Zombie 청소 시뮬레이션
     */
    private List<SimulationResult> simulateCleanup(SimulateRequest request) {
        // TODO: 구현
        return new ArrayList<>();
    }
    
    // === 헬퍼 메서드 ===
    
    /**
     * 일일 중단 시간 계산 (시간)
     */
    private double calculateDailyOffHours(String stopAt, String startAt) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime stop = LocalTime.parse(stopAt, formatter);
            LocalTime start = LocalTime.parse(startAt, formatter);
            
            Duration duration;
            if (start.isAfter(stop)) {
                // 다음날까지
                duration = Duration.between(stop, start.plusHours(24));
            } else {
                duration = Duration.between(stop, start);
            }
            
            return duration.toHours();
        } catch (Exception e) {
            log.warn("Failed to parse time, using default 12 hours", e);
            return 12.0;  // 기본값
        }
    }
    
    /**
     * 리소스 정보 조회 (실제 EC2 인스턴스 정보)
     */
    private ResourceInfo getResourceInfo(String resourceId) {
        try {
            // 활성 AWS 계정에서 인스턴스 찾기
            List<AwsAccount> activeAccounts = awsAccountRepository.findAll().stream()
                    .filter(account -> Boolean.TRUE.equals(account.getActive()))
                    .collect(java.util.stream.Collectors.toList());
            
            for (AwsAccount account : activeAccounts) {
                try {
                    String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                    List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                    
                    for (AwsEc2InstanceResponse instance : instances) {
                        if (resourceId.equals(instance.getInstanceId())) {
                            // 태그 정보 수집
                            Map<String, String> tags = new HashMap<>();
                            if (instance.getName() != null && !instance.getName().isEmpty()) {
                                tags.put("Name", instance.getName());
                            }
                            tags.put("env", "dev"); // 기본값
                            
                            return ResourceInfo.builder()
                                    .id(resourceId)
                                    .csp("AWS")
                                    .service("EC2")
                                    .region(region)
                                    .project("default")
                                    .tags(tags)
                                    .instanceType(instance.getInstanceType()) // 인스턴스 타입 추가
                                    .build();
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to fetch instances for account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get resource info for {}: {}", resourceId, e.getMessage());
        }
        
        // 기본값 반환
        return ResourceInfo.builder()
                .id(resourceId)
                .csp("AWS")
                .service("EC2")
                .region("us-east-1")
                .project("default")
                .tags(Map.of("env", "dev"))
                .build();
    }
    
    /**
     * EC2 인스턴스 타입별 시간당 가격 (USD, ap-northeast-2 기준)
     * 실제 AWS 가격을 반영 (2024년 기준)
     */
    private static final Map<String, Double> EC2_INSTANCE_PRICING = Map.ofEntries(
            // t 시리즈
            Map.entry("t2.micro", 0.0116),
            Map.entry("t2.small", 0.023),
            Map.entry("t2.medium", 0.0464),
            Map.entry("t3.micro", 0.0104),
            Map.entry("t3.small", 0.0208),
            Map.entry("t3.medium", 0.0416),
            Map.entry("t3.large", 0.0832),
            Map.entry("t4g.micro", 0.0084),
            Map.entry("t4g.small", 0.0168),
            // m 시리즈
            Map.entry("m5.large", 0.096),
            Map.entry("m5.xlarge", 0.192),
            Map.entry("m5.2xlarge", 0.384),
            Map.entry("m6i.large", 0.108),
            Map.entry("m6i.xlarge", 0.216),
            Map.entry("m6i.2xlarge", 0.432),
            Map.entry("m7i.large", 0.120),
            Map.entry("m7i.xlarge", 0.240),
            Map.entry("m7i.2xlarge", 0.480),
            Map.entry("m7i-flex.large", 0.108),
            Map.entry("m7i-flex.xlarge", 0.216),
            Map.entry("m7i-flex.2xlarge", 0.432),
            // c 시리즈
            Map.entry("c5.large", 0.085),
            Map.entry("c5.xlarge", 0.17),
            Map.entry("c5.2xlarge", 0.34),
            Map.entry("c6i.large", 0.085),
            Map.entry("c6i.xlarge", 0.17),
            Map.entry("c7i.large", 0.095),
            Map.entry("c7i.xlarge", 0.19),
            // r 시리즈
            Map.entry("r5.large", 0.126),
            Map.entry("r5.xlarge", 0.252),
            Map.entry("r6i.large", 0.1512),
            Map.entry("r6i.xlarge", 0.3024)
    );
    
    /**
     * 가격 정보 조회 (실제 EC2 인스턴스 타입 기반)
     */
    private PricingInfo getPricingInfo(ResourceInfo resource) {
        String instanceType = resource.getInstanceType();
        Double unitPrice = null;
        
        if (instanceType != null) {
            // 인스턴스 타입별 실제 가격 조회
            unitPrice = EC2_INSTANCE_PRICING.get(instanceType.toLowerCase());
        }
        
        // 가격을 찾지 못한 경우 기본값 사용 (더 현실적인 가격)
        if (unitPrice == null) {
            // 인스턴스 타입이 없거나 매핑되지 않은 경우, 일반적인 중간 크기 인스턴스 가격 사용
            unitPrice = 0.15; // 시간당 $0.15 (약 m5.large 수준)
            log.debug("Instance type {} not found in pricing map, using default price {}", instanceType, unitPrice);
        }
        
        return PricingInfo.builder()
                .unit("hour")
                .unitPrice(unitPrice)
                .commitmentApplicable(true)
                .commitmentPrice(unitPrice * 0.5) // 약정 시 50% 할인
                .build();
    }
    
    /**
     * 사용량 메트릭 조회
     */
    private UsageMetrics getUsageMetrics(String resourceId, ResourceInfo resource) {
        // TODO: 실제 메트릭 조회 (CloudWatch 등)
        return UsageMetrics.builder()
                .avg(30.0)
                .p95(60.0)
                .p99(80.0)
                .idleRatio(0.6)
                .schedulePattern("weekdays")
                .uptimeDays(365L)
                .build();
    }
    
    /**
     * Off-hours에서 제외할 리소스 판단
     */
    private boolean shouldExcludeFromOffHours(ResourceInfo resource) {
        Map<String, String> tags = resource.getTags();
        if (tags == null) {
            return false;
        }
        
        // env=dev는 제외하지 않음 (개발 환경은 오히려 적용 대상)
        // owner 미지정은 제외
        return tags.containsKey("owner") && tags.get("owner").isEmpty();
    }
}

