package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsCostService {
    
    private final AwsAccountRepository accountRepository;
    
    /**
     * AWS 계정의 비용 조회
     * 
     * @param accountId AWS 계정 ID
     * @param startDate 시작 날짜 (YYYY-MM-DD 형식)
     * @param endDate 종료 날짜 (YYYY-MM-DD 형식)
     * @return 일별 비용 목록
     */
    public List<DailyCost> getCosts(Long accountId, String startDate, String endDate) {
        AwsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AWS 계정을 찾을 수 없습니다."));
        
        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }
        
        log.info("Fetching AWS costs for account {} from {} to {}", accountId, startDate, endDate);
        
        try (CostExplorerClient costExplorerClient = createCostExplorerClient(account)) {
            GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder()
                            .start(startDate)
                            .end(endDate)
                            .build())
                    .granularity(Granularity.DAILY)
                    .metrics("BlendedCost", "UnblendedCost")
                    .groupBy(GroupDefinition.builder()
                            .type(GroupDefinitionType.DIMENSION)
                            .key("SERVICE")
                            .build())
                    .build();
            
            GetCostAndUsageResponse response = costExplorerClient.getCostAndUsage(request);
            
            List<DailyCost> dailyCosts = new ArrayList<>();
            
            if (response.resultsByTime() == null || response.resultsByTime().isEmpty()) {
                log.warn("No cost data returned for account {} from {} to {}", accountId, startDate, endDate);
                return dailyCosts; // 빈 리스트 반환
            }
            
            for (ResultByTime result : response.resultsByTime()) {
                String date = result.timePeriod().start();
                double totalCost = 0.0;
                List<ServiceCost> serviceCosts = new ArrayList<>();
                
                // groups가 null이거나 비어있을 수 있음 (비용이 없는 경우)
                if (result.groups() != null && !result.groups().isEmpty()) {
                    for (Group group : result.groups()) {
                        if (group.keys() != null && !group.keys().isEmpty() && 
                            group.metrics() != null && group.metrics().containsKey("BlendedCost")) {
                            try {
                                String service = group.keys().get(0);
                                String amount = group.metrics().get("BlendedCost").amount();
                                if (amount != null && !amount.isEmpty()) {
                                    double cost = Double.parseDouble(amount);
                                    totalCost += cost;
                                    
                                    if (cost > 0) {
                                        serviceCosts.add(new ServiceCost(service, cost));
                                    }
                                }
                            } catch (NumberFormatException e) {
                                log.warn("Failed to parse cost amount for service: {}", e.getMessage());
                            }
                        }
                    }
                }
                
                dailyCosts.add(new DailyCost(date, totalCost, serviceCosts));
            }
            
            log.info("Successfully fetched {} days of cost data for account {} (total cost: {})", 
                    dailyCosts.size(), accountId, 
                    dailyCosts.stream().mapToDouble(DailyCost::totalCost).sum());
            return dailyCosts;
            
        } catch (software.amazon.awssdk.services.costexplorer.model.CostExplorerException e) {
            log.error("AWS Cost Explorer API error for account {}: {} - {}", 
                    accountId, e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage());
            // 권한 부족이나 Cost Explorer 미활성화 등의 경우 빈 리스트 반환
            if (e.awsErrorDetails().errorCode().equals("AccessDeniedException") ||
                e.awsErrorDetails().errorCode().equals("ValidationException")) {
                log.warn("Cost Explorer access denied or validation error. Returning empty cost data.");
                return new ArrayList<>();
            }
            throw new RuntimeException("AWS 비용 조회 실패: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("Failed to fetch AWS costs for account {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("AWS 비용 조회 실패: " + e.getMessage());
        }
    }
    
    /**
     * AWS 계정의 월별 총 비용 조회
     * 
     * @param accountId AWS 계정 ID
     * @param year 연도
     * @param month 월 (1-12)
     * @return 월별 총 비용
     */
    public MonthlyCost getMonthlyCost(Long accountId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1);
        
        String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        List<DailyCost> dailyCosts = getCosts(accountId, startDateStr, endDateStr);
        
        double totalCost = dailyCosts.stream()
                .mapToDouble(DailyCost::totalCost)
                .sum();
        
        return new MonthlyCost(year, month, totalCost);
    }
    
    /**
     * 모든 활성 AWS 계정의 총 비용 조회
     * 
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 계정별 비용 목록
     */
    public List<AccountCost> getAllAccountsCosts(String startDate, String endDate) {
        List<AwsAccount> activeAccounts = accountRepository.findByActiveTrue();
        List<AccountCost> accountCosts = new ArrayList<>();
        
        for (AwsAccount account : activeAccounts) {
            try {
                List<DailyCost> dailyCosts = getCosts(account.getId(), startDate, endDate);
                double totalCost = dailyCosts.stream()
                        .mapToDouble(DailyCost::totalCost)
                        .sum();
                
                accountCosts.add(new AccountCost(account.getId(), account.getName(), totalCost));
            } catch (Exception e) {
                log.error("Failed to fetch costs for account {}: {}", account.getId(), e.getMessage());
                // 계정별로 실패해도 다른 계정은 계속 조회
            }
        }
        
        return accountCosts;
    }
    
    private CostExplorerClient createCostExplorerClient(AwsAccount account) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                account.getAccessKeyId(),
                account.getSecretKeyEnc() // @Convert에 의해 자동 복호화
        );
        
        return CostExplorerClient.builder()
                .region(Region.AWS_GLOBAL) // Cost Explorer는 글로벌 서비스
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
    
    // DTO 클래스들
    public record DailyCost(String date, double totalCost, List<ServiceCost> services) {}
    public record ServiceCost(String service, double cost) {}
    public record MonthlyCost(int year, int month, double totalCost) {}
    public record AccountCost(Long accountId, String accountName, double totalCost) {}
}

