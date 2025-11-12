package com.budgetops.backend.simulator.engine;

import com.budgetops.backend.simulator.dto.PricingInfo;
import com.budgetops.backend.simulator.dto.UsageMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 공통 비용 계산 엔진
 * 모든 CSP/서비스에 동일하게 적용되는 계산식
 */
@Slf4j
@Component
public class CostEngine {
    
    /**
     * 현재 비용 계산: Σ(단가 × 사용량/시간)
     * 
     * @param unitPrice 단가
     * @param usage 사용량 (시간, GB, 요청 수 등)
     * @param unit 단위 ("hour", "GB", "request", "GB-month")
     * @return 현재 비용
     */
    public Double calculateCurrentCost(Double unitPrice, Double usage, String unit) {
        if (unitPrice == null || usage == null) {
            return 0.0;
        }
        
        // 단위별 월간 비용 계산
        double monthlyCost = switch (unit) {
            case "hour" -> unitPrice * usage * 24 * 30;  // 시간당 × 24시간 × 30일
            case "GB" -> unitPrice * usage;  // GB당
            case "request" -> unitPrice * usage;  // 요청당
            case "GB-month" -> unitPrice * usage;  // GB-월
            case "slot" -> unitPrice * usage * 24 * 30;  // 슬롯당 시간
            default -> {
                log.warn("Unknown unit: {}, defaulting to 0", unit);
                yield 0.0;
            }
        };
        
        return monthlyCost;
    }
    
    /**
     * 변경 비용 계산: Σ(단가′ × 사용량′/시간′)
     * 
     * @param newUnitPrice 변경 후 단가
     * @param newUsage 변경 후 사용량
     * @param unit 단위
     * @return 변경 비용
     */
    public Double calculateNewCost(Double newUnitPrice, Double newUsage, String unit) {
        return calculateCurrentCost(newUnitPrice, newUsage, unit);
    }
    
    /**
     * 절감액 계산: 현재비용 - 변경비용
     */
    public Double calculateSavings(Double currentCost, Double newCost) {
        if (currentCost == null || newCost == null) {
            return 0.0;
        }
        return Math.max(0.0, currentCost - newCost);
    }
    
    /**
     * 리스크 스코어 계산: f(피크 사용률, 95p/99p, 가용성 정책, SLO) 0~1
     * 
     * @param metrics 사용량 메트릭
     * @param availabilityPolicy 가용성 정책 (high, medium, low)
     * @return 리스크 스코어 (0~1, 높을수록 위험)
     */
    public Double calculateRiskScore(UsageMetrics metrics, String availabilityPolicy) {
        if (metrics == null) {
            return 0.5;  // 기본값
        }
        
        double risk = 0.0;
        
        // 피크 사용률 기반 리스크 (p99가 높을수록 위험)
        if (metrics.getP99() != null) {
            risk += metrics.getP99() * 0.3;  // 최대 0.3 기여
        }
        
        // 가용성 정책 기반 리스크
        double availabilityRisk = switch (availabilityPolicy != null ? availabilityPolicy.toLowerCase() : "medium") {
            case "high" -> 0.5;  // 높은 가용성 요구 = 높은 리스크
            case "medium" -> 0.3;
            case "low" -> 0.1;
            default -> 0.3;
        };
        risk += availabilityRisk * 0.4;  // 최대 0.4 기여
        
        // 유휴 비율이 낮을수록 위험 (항상 사용 중 = 중단 시 영향 큼)
        if (metrics.getIdleRatio() != null) {
            risk += (1.0 - metrics.getIdleRatio()) * 0.3;  // 최대 0.3 기여
        }
        
        return Math.min(1.0, Math.max(0.0, risk));
    }
    
    /**
     * 우선순위 점수 계산: 절감액 × 확신도(1-리스크) ÷ 적용난이도
     * 
     * @param savings 절감액
     * @param riskScore 리스크 스코어
     * @param difficulty 적용 난이도 (1~5, 높을수록 어려움)
     * @return 우선순위 점수
     */
    public Double calculatePriorityScore(Double savings, Double riskScore, Integer difficulty) {
        if (savings == null || riskScore == null) {
            return 0.0;
        }
        
        double confidence = 1.0 - riskScore;  // 확신도
        double difficultyFactor = difficulty != null && difficulty > 0 ? (double) difficulty : 1.0;
        
        return savings * confidence / difficultyFactor;
    }
    
    /**
     * Off-hours 절감액 계산
     * 공식: (daily_off_hours/24) * on_demand_hourly_price * 30
     * 
     * @param hourlyPrice 시간당 단가
     * @param dailyOffHours 일일 중단 시간 (시간)
     * @return 월 절감액
     */
    public Double calculateOffHoursSavings(Double hourlyPrice, Double dailyOffHours) {
        if (hourlyPrice == null || dailyOffHours == null) {
            return 0.0;
        }
        return (dailyOffHours / 24.0) * hourlyPrice * 30.0;
    }
    
    /**
     * Commitment 절감액 계산
     * 
     * @param onDemandPrice 온디맨드 단가
     * @param commitmentPrice 약정 단가
     * @param commitLevel 커밋 레벨 (0.5, 0.7, 0.9)
     * @param usage 사용량
     * @param unit 단위
     * @return 월 절감액
     */
    public Double calculateCommitmentSavings(
            Double onDemandPrice, 
            Double commitmentPrice, 
            Double commitLevel, 
            Double usage, 
            String unit) {
        if (onDemandPrice == null || commitmentPrice == null || commitLevel == null || usage == null) {
            return 0.0;
        }
        
        double currentCost = calculateCurrentCost(onDemandPrice, usage, unit);
        double committedUsage = usage * commitLevel;
        double onDemandUsage = usage * (1.0 - commitLevel);
        
        double newCost = calculateCurrentCost(commitmentPrice, committedUsage, unit) +
                        calculateCurrentCost(onDemandPrice, onDemandUsage, unit);
        
        return calculateSavings(currentCost, newCost);
    }
    
    /**
     * Storage 수명주기 절감액 계산
     * 
     * @param currentTierPrice 현재 스토리지 클래스 단가
     * @param targetTierPrice 목표 스토리지 클래스 단가
     * @param sizeGB 용량 (GB)
     * @return 월 절감액
     */
    public Double calculateStorageLifecycleSavings(
            Double currentTierPrice, 
            Double targetTierPrice, 
            Double sizeGB) {
        if (currentTierPrice == null || targetTierPrice == null || sizeGB == null) {
            return 0.0;
        }
        
        double currentCost = calculateCurrentCost(currentTierPrice, sizeGB, "GB-month");
        double newCost = calculateCurrentCost(targetTierPrice, sizeGB, "GB-month");
        
        return calculateSavings(currentCost, newCost);
    }
}

