package com.budgetops.backend.aws.constants;

import java.util.Map;

/**
 * AWS 프리티어 한도 정보
 * 각 서비스별 프리티어 한도를 정의합니다.
 */
public class FreeTierLimits {
    
    /**
     * EC2 프리티어 한도
     * - t2.micro, t3.micro, t4g.micro 인스턴스 타입
     * - 월 750시간 무료
     */
    public static final double EC2_FREE_TIER_HOURS_PER_MONTH = 750.0;
    
    /**
     * S3 프리티어 한도
     * - 스토리지: 5GB
     * - PUT 요청: 20,000건
     * - GET 요청: 20,000건
     */
    public static final double S3_FREE_TIER_STORAGE_GB = 5.0;
    public static final long S3_FREE_TIER_PUT_REQUESTS = 20000L;
    public static final long S3_FREE_TIER_GET_REQUESTS = 20000L;
    
    /**
     * RDS 프리티어 한도
     * - db.t2.micro, db.t3.micro 인스턴스 타입
     * - 월 750시간 무료
     * - 스토리지: 20GB
     */
    public static final double RDS_FREE_TIER_HOURS_PER_MONTH = 750.0;
    public static final double RDS_FREE_TIER_STORAGE_GB = 20.0;
    
    /**
     * Lambda 프리티어 한도
     * - 요청: 월 1,000,000건
     * - 컴퓨팅 시간: 월 400,000 GB-초
     */
    public static final long LAMBDA_FREE_TIER_REQUESTS = 1000000L;
    public static final long LAMBDA_FREE_TIER_COMPUTE_GB_SECONDS = 400000L;
    
    /**
     * 데이터 전송 프리티어 한도
     * - 아웃바운드: 월 1GB (첫 12개월)
     * - 인바운드: 무제한
     */
    public static final double DATA_TRANSFER_OUT_FREE_TIER_GB = 1.0;
    
    /**
     * EC2 인스턴스 타입별 시간당 요금 (USD)
     * 프리티어 초과 시 적용되는 요금
     */
    public static final Map<String, Double> EC2_INSTANCE_PRICING = Map.of(
        "t2.micro", 0.0116,
        "t3.micro", 0.0104,
        "t4g.micro", 0.0084,
        "t2.small", 0.023,
        "t3.small", 0.0208,
        "t4g.small", 0.0168
    );
    
    /**
     * 서비스별 프리티어 한도 확인
     */
    public static boolean isFreeTierEligible(String service, String resourceType, double usage) {
        return switch (service.toUpperCase()) {
            case "EC2" -> usage <= EC2_FREE_TIER_HOURS_PER_MONTH;
            case "S3" -> resourceType != null && resourceType.equals("STORAGE") 
                ? usage <= S3_FREE_TIER_STORAGE_GB 
                : true; // 요청 수는 별도로 확인 필요
            case "RDS" -> usage <= RDS_FREE_TIER_HOURS_PER_MONTH;
            case "LAMBDA" -> resourceType != null && resourceType.equals("REQUESTS")
                ? usage <= LAMBDA_FREE_TIER_REQUESTS
                : usage <= LAMBDA_FREE_TIER_COMPUTE_GB_SECONDS;
            default -> false;
        };
    }
    
    /**
     * EC2 인스턴스 타입이 프리티어 대상인지 확인
     */
    public static boolean isFreeTierInstanceType(String instanceType) {
        if (instanceType == null) return false;
        String lowerType = instanceType.toLowerCase();
        return lowerType.equals("t2.micro") || 
               lowerType.equals("t3.micro") || 
               lowerType.equals("t4g.micro");
    }
    
    /**
     * 프리티어 초과 시 예상 비용 계산 (EC2)
     */
    public static double calculateEstimatedCostIfExceeded(String service, String resourceType, 
                                                          double usage, String instanceType) {
        if (!isFreeTierEligible(service, resourceType, usage)) {
            double freeTierLimit = switch (service.toUpperCase()) {
                case "EC2" -> EC2_FREE_TIER_HOURS_PER_MONTH;
                case "S3" -> S3_FREE_TIER_STORAGE_GB;
                case "RDS" -> RDS_FREE_TIER_HOURS_PER_MONTH;
                default -> 0.0;
            };
            
            double exceededUsage = usage - freeTierLimit;
            
            if (service.equalsIgnoreCase("EC2") && instanceType != null) {
                Double hourlyRate = EC2_INSTANCE_PRICING.get(instanceType.toLowerCase());
                if (hourlyRate != null) {
                    return exceededUsage * hourlyRate;
                }
            }
        }
        return 0.0;
    }
}

