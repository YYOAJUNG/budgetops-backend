package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.GcpAccountDailyCostsResponse;
import com.budgetops.backend.gcp.dto.GcpAllAccountsCostsResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.gax.paging.Page;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.DatasetId;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class GcpCostService {

    private final GcpAccountRepository accountRepository;

    /**
     * GCP 계정의 비용 조회 (일별)
     * 
     * @param accountId GCP 계정 ID
     * @param startDate 시작 날짜 (YYYY-MM-DD 형식)
     * @param endDate 종료 날짜 (YYYY-MM-DD 형식)
     * @return 계정별 일별 비용 정보
     */
    @Transactional(readOnly = true)
    public GcpAccountDailyCostsResponse getCosts(Long accountId, String startDate, String endDate) {
        GcpAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "GCP 계정을 찾을 수 없습니다."));

        if (account.getBillingAccountId() == null || account.getBillingAccountId().isBlank()) {
            log.warn("GCP 계정 {}에 빌링 계정 ID가 설정되지 않았습니다.", accountId);
            GcpAccountDailyCostsResponse response = new GcpAccountDailyCostsResponse();
            response.setAccountId(accountId);
            response.setAccountName(account.getName());
            response.setCurrency("USD");
            response.setTotalGrossCost(0.0);
            response.setTotalCreditUsed(0.0);
            response.setTotalNetCost(0.0);
            response.setTotalDisplayNetCost(0.0);
            response.setDailyCosts(new ArrayList<>());
            return response;
        }

        if (account.getBillingExportDatasetId() == null || account.getBillingExportDatasetId().isBlank()) {
            log.warn("GCP 계정 {}에 빌링 내보내기 데이터셋이 설정되지 않았습니다.", accountId);
            GcpAccountDailyCostsResponse response = new GcpAccountDailyCostsResponse();
            response.setAccountId(accountId);
            response.setAccountName(account.getName());
            response.setCurrency("USD");
            response.setTotalGrossCost(0.0);
            response.setTotalCreditUsed(0.0);
            response.setTotalNetCost(0.0);
            response.setTotalDisplayNetCost(0.0);
            response.setDailyCosts(new ArrayList<>());
            return response;
        }

        log.info("Fetching GCP costs for account {} from {} to {}", accountId, startDate, endDate);

        try {
            ServiceAccountCredentials credentials = GcpCredentialParser.parse(account.getEncryptedServiceAccountKey());
            String projectId = account.getProjectId();
            String datasetId = account.getBillingExportDatasetId();
            String billingIdForTable = account.getBillingAccountId().replace('-', '_');

            BigQuery bigQuery = BigQueryOptions.newBuilder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build()
                    .getService();

            // Billing Export 테이블 목록 찾기
            List<String> tableNames = findBillingTableNames(bigQuery, projectId, datasetId, billingIdForTable);
            if (tableNames.isEmpty()) {
                log.warn("Billing export 테이블을 찾을 수 없습니다. accountId: {}, billingId: {}", 
                        accountId, account.getBillingAccountId());
                GcpAccountDailyCostsResponse response = new GcpAccountDailyCostsResponse();
                response.setAccountId(accountId);
                response.setAccountName(account.getName());
                response.setCurrency("USD");
                response.setTotalNetCost(0.0);
                response.setTotalGrossCost(0.0);
                response.setTotalCreditUsed(0.0);
                response.setTotalDisplayNetCost(0.0);
                response.setDailyCosts(new ArrayList<>());
                return response;
            }

            // BigQuery 쿼리 실행
            String query = buildDailyCostQuery(projectId, datasetId, tableNames, startDate, endDate);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = bigQuery.query(queryConfig);

            // 결과 파싱
            ParseResult parseResult = parseDailyCosts(result);
            
            // totalGrossCost, totalCreditUsed, totalNetCost 계산
            double totalGrossCost = roundToFirstDecimal(parseResult.dailyCosts().stream()
                    .mapToDouble(GcpAccountDailyCostsResponse.DailyCost::getGrossCost)
                    .sum());
            double totalCreditUsed = roundToFirstDecimal(parseResult.dailyCosts().stream()
                    .mapToDouble(GcpAccountDailyCostsResponse.DailyCost::getCreditUsed)
                    .sum());
            double totalNetCost = roundToFirstDecimal(totalGrossCost - totalCreditUsed);
            double totalDisplayNetCost = Math.max(0, totalNetCost);
            
            GcpAccountDailyCostsResponse response = new GcpAccountDailyCostsResponse();
            response.setAccountId(accountId);
            response.setAccountName(account.getName());
            response.setCurrency(parseResult.currency());
            response.setTotalGrossCost(totalGrossCost);
            response.setTotalCreditUsed(totalCreditUsed);
            response.setTotalNetCost(totalNetCost);
            response.setTotalDisplayNetCost(totalDisplayNetCost);
            response.setDailyCosts(parseResult.dailyCosts());
            
            log.info("Successfully fetched {} days of cost data for account {} (total gross cost: {}, total credit used: {}, total net cost: {})", 
                    parseResult.dailyCosts().size(), accountId, totalGrossCost, totalCreditUsed, totalNetCost);
            
            return response;

        } catch (Exception e) {
            log.error("Failed to fetch GCP costs for account {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("GCP 비용 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * GCP 계정의 월별 비용 조회
     * 
     * @param accountId GCP 계정 ID
     * @param year 연도
     * @param month 월 (1-12)
     * @return 월별 비용 정보
     */
    @Transactional(readOnly = true)
    public MonthlyCost getMonthlyCost(Long accountId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1);
        
        String startDateStr = startDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        String endDateStr = endDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        
        GcpAccountDailyCostsResponse dailyCostsResponse = getCosts(accountId, startDateStr, endDateStr);
        
        return new MonthlyCost(
                dailyCostsResponse.getAccountId(),
                dailyCostsResponse.getAccountName(),
                year,
                month,
                dailyCostsResponse.getTotalGrossCost(),
                dailyCostsResponse.getTotalCreditUsed(),
                dailyCostsResponse.getTotalNetCost(),
                Math.max(0, dailyCostsResponse.getTotalNetCost()),
                dailyCostsResponse.getCurrency()
        );
    }

    /**
     * 모든 GCP 계정의 비용 통합 조회
     * 
     * @param startDate 시작 날짜 (YYYY-MM-DD 형식)
     * @param endDate 종료 날짜 (YYYY-MM-DD 형식)
     * @return 모든 계정의 비용 통합 정보
     * 
     * TODO: 계정별로 통화가 다른 경우 처리 필요
     * - 현재는 모든 계정의 비용을 단순 합산하고 있으나, 통화가 다르면 의미 없는 값이 됨
     * - 해결 방안:
     *   1. 통화 변환 API를 사용하여 기준 통화로 변환 후 합산
     *   2. 통화별로 그룹화하여 summary를 통화별로 제공
     *   3. 통화가 다른 경우 에러 또는 경고 반환
     */
    @Transactional(readOnly = true)
    public GcpAllAccountsCostsResponse getAllAccountsCosts(String startDate, String endDate) {
        List<GcpAccount> accounts = accountRepository.findAll();
        
        GcpAllAccountsCostsResponse response = new GcpAllAccountsCostsResponse();
        List<GcpAllAccountsCostsResponse.AccountCost> accountCosts = new ArrayList<>();
        
        double totalGrossCost = 0.0;
        double totalCreditUsed = 0.0;
        double totalNetCost = 0.0;
        String currency = "USD";
        boolean currencySet = false;
        
        for (GcpAccount account : accounts) {
            try {
                GcpAccountDailyCostsResponse accountCostResponse = getCosts(account.getId(), startDate, endDate);
                
                GcpAllAccountsCostsResponse.AccountCost accountCost = new GcpAllAccountsCostsResponse.AccountCost();
                accountCost.setAccountId(accountCostResponse.getAccountId());
                accountCost.setAccountName(accountCostResponse.getAccountName());
                accountCost.setCurrency(accountCostResponse.getCurrency());
                accountCost.setTotalGrossCost(accountCostResponse.getTotalGrossCost());
                accountCost.setTotalCreditUsed(accountCostResponse.getTotalCreditUsed());
                accountCost.setTotalNetCost(accountCostResponse.getTotalNetCost());
                accountCost.setTotalDisplayNetCost(accountCostResponse.getTotalDisplayNetCost());
                accountCosts.add(accountCost);
                
                // 통화는 첫 번째 계정의 통화를 사용 (모든 계정이 같은 통화를 사용한다고 가정)
                // TODO: 통화가 다른 경우 처리 필요
                if (!currencySet && accountCostResponse.getCurrency() != null) {
                    currency = accountCostResponse.getCurrency();
                    currencySet = true;
                }
                
                // 총합 계산
                // TODO: 통화가 다른 경우 단순 합산은 의미 없음 - 통화 변환 필요
                totalGrossCost += accountCostResponse.getTotalGrossCost();
                totalCreditUsed += accountCostResponse.getTotalCreditUsed();
                totalNetCost += accountCostResponse.getTotalNetCost();
                
            } catch (Exception e) {
                log.warn("Failed to fetch costs for account {}: {}", account.getId(), e.getMessage());
                // 계정 조회 실패 시 해당 계정은 건너뛰고 계속 진행
            }
        }
        
        // Summary 생성
        GcpAllAccountsCostsResponse.Summary summary = new GcpAllAccountsCostsResponse.Summary();
        summary.setCurrency(currency);
        summary.setTotalGrossCost(roundToFirstDecimal(totalGrossCost));
        summary.setTotalCreditUsed(roundToFirstDecimal(totalCreditUsed));
        double finalTotalNetCost = roundToFirstDecimal(totalNetCost);
        summary.setTotalNetCost(finalTotalNetCost);
        summary.setTotalDisplayNetCost(Math.max(0, finalTotalNetCost));
        
        response.setSummary(summary);
        response.setAccounts(accountCosts);
        
        return response;
    }

    /**
     * 특정 회원의 모든 GCP 계정 비용 합계
     */
    @Transactional(readOnly = true)
    public double getMemberTotalNetCost(Long memberId, String startDate, String endDate) {
        List<GcpAccount> accounts = accountRepository.findByOwnerId(memberId);
        if (accounts.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (GcpAccount account : accounts) {
            try {
                GcpAccountDailyCostsResponse response = getCosts(account.getId(), startDate, endDate);
                total += Math.max(0.0, response.getTotalNetCost());
            } catch (Exception e) {
                log.warn("Failed to fetch GCP cost for account {}: {}", account.getId(), e.getMessage());
            }
        }
        return roundToFirstDecimal(total);
    }

    /**
     * 특정 회원의 계정별 비용 요약
     */
    @Transactional(readOnly = true)
    public List<AccountCost> getMemberAccountCosts(Long memberId, String startDate, String endDate) {
        List<GcpAccount> accounts = accountRepository.findByOwnerId(memberId);
        List<AccountCost> results = new ArrayList<>();
        for (GcpAccount account : accounts) {
            String accountName = optionalName(account);
            try {
                GcpAccountDailyCostsResponse response = getCosts(account.getId(), startDate, endDate);
                results.add(new AccountCost(
                        account.getId(),
                        accountName,
                        Math.max(0.0, response.getTotalNetCost()),
                        response.getCurrency()
                ));
            } catch (Exception e) {
                log.warn("Failed to fetch GCP cost for account {}: {}", account.getId(), e.getMessage());
                results.add(new AccountCost(
                        account.getId(),
                        accountName,
                        0.0,
                        "USD"
                ));
            }
        }
        return results;
    }

    private String optionalName(GcpAccount account) {
        if (account.getName() != null && !account.getName().isBlank()) {
            return account.getName();
        }
        if (account.getProjectId() != null && !account.getProjectId().isBlank()) {
            return account.getProjectId();
        }
        return "GCP #" + account.getId();
    }
    
    /**
     * Billing Export 테이블 이름 목록 찾기
     * 실제 존재하는 테이블들을 모두 찾아서 반환
     */
    private List<String> findBillingTableNames(BigQuery bigQuery, String projectId, String datasetId, String billingIdForTable) {
        List<String> tableNames = new ArrayList<>();
        DatasetId dataset = DatasetId.of(projectId, datasetId);
        
        try {
            Page<Table> tables = bigQuery.listTables(dataset);
            
            for (Table entry : tables.iterateAll()) {
                String tableId = entry.getTableId().getTable();
                
                // 테이블 이름이 주어진 billingAccountId와 일치하는지 확인
                if (isBillingTableForAccount(tableId, billingIdForTable)) {
                    tableNames.add(tableId);
                }
            }
            
            log.debug("Found {} billing export table(s) for billingId: {}", tableNames.size(), billingIdForTable);
            
        } catch (Exception e) {
            log.error("Failed to list tables in dataset {}: {}", datasetId, e.getMessage());
        }
        
        return tableNames;
    }
    
    /**
     * 테이블 이름이 주어진 billingAccountId와 일치하는지 확인
     * GcpBillingAccountVerifier의 로직과 동일
     */
    private boolean isBillingTableForAccount(String tableName, String billingIdForTable) {
        // 직접 포함 여부 확인
        if (tableName.contains(billingIdForTable)) {
            int index = tableName.indexOf(billingIdForTable);
            if ((index == 0 || !Character.isLetterOrDigit(tableName.charAt(index - 1))) &&
                (index + billingIdForTable.length() >= tableName.length() || 
                 !Character.isLetterOrDigit(tableName.charAt(index + billingIdForTable.length())))) {
                return true;
            }
        }
        
        // 패턴 매칭: gcp_billing_export_v1_XXXXXX_XXXXXX_XXXXXX
        if (tableName.startsWith("gcp_billing_export_v1_")) {
            String afterPrefix = tableName.substring("gcp_billing_export_v1_".length());
            if (afterPrefix.startsWith(billingIdForTable)) {
                String remaining = afterPrefix.substring(billingIdForTable.length());
                return remaining.isEmpty() || remaining.startsWith("_");
            }
        }
        
        // 패턴 매칭: gcp_billing_export_XXXXXX_XXXXXX_XXXXXX (v1 없이)
        if (tableName.startsWith("gcp_billing_export_")) {
            String afterPrefix = tableName.substring("gcp_billing_export_".length());
            if (afterPrefix.startsWith(billingIdForTable)) {
                String remaining = afterPrefix.substring(billingIdForTable.length());
                return remaining.isEmpty() || remaining.startsWith("_");
            }
        }
        
        return false;
    }

    /**
     * 일별 비용 조회 쿼리 생성
     * 실제 존재하는 테이블 목록을 사용하여 UNION ALL로 조회
     */
    private String buildDailyCostQuery(String projectId, String datasetId, List<String> tableNames, String startDate, String endDate) {
        if (tableNames.isEmpty()) {
            throw new IllegalArgumentException("테이블 목록이 비어있습니다.");
        }
        
        // 단일 테이블인 경우
        if (tableNames.size() == 1) {
            return String.format(
                "SELECT " +
                "  DATE(usage_start_time) as date, " +
                "  ROUND(SUM(CASE WHEN cost > 0 THEN cost ELSE 0 END), 1) as gross_cost, " +
                "  ROUND(SUM(COALESCE((SELECT SUM(ABS(amount)) FROM UNNEST(credits) WHERE amount < 0), 0)), 1) as credit_used_from_credits, " +
                "  ANY_VALUE(currency) as currency " +
                "FROM `%s.%s.%s` " +
                "WHERE DATE(usage_start_time) >= '%s' " +
                "  AND DATE(usage_start_time) < '%s' " +
                "GROUP BY date " +
                "ORDER BY date",
                projectId, datasetId, tableNames.get(0), startDate, endDate
            );
        }
        
        // 여러 테이블인 경우 UNION ALL 사용
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < tableNames.size(); i++) {
            if (i > 0) {
                query.append(" UNION ALL ");
            }
            query.append(String.format(
                "SELECT " +
                "  DATE(usage_start_time) as date, " +
                "  CASE WHEN cost > 0 THEN cost ELSE 0 END as gross_cost, " +
                "  COALESCE((SELECT SUM(ABS(amount)) FROM UNNEST(credits) WHERE amount < 0), 0) as credit_used_from_credits, " +
                "  currency " +
                "FROM `%s.%s.%s` " +
                "WHERE DATE(usage_start_time) >= '%s' " +
                "  AND DATE(usage_start_time) < '%s'",
                projectId, datasetId, tableNames.get(i), startDate, endDate
            ));
        }
        
        // 최종 집계 (반올림 적용)
        return String.format(
            "SELECT " +
            "  date, " +
            "  ROUND(SUM(gross_cost), 1) as gross_cost, " +
            "  ROUND(SUM(credit_used_from_credits), 1) as credit_used_from_credits, " +
            "  ANY_VALUE(currency) as currency " +
            "FROM (%s) " +
            "GROUP BY date " +
            "ORDER BY date",
            query.toString()
        );
    }

    /**
     * 파싱 결과를 담는 레코드
     */
    private record ParseResult(
        List<GcpAccountDailyCostsResponse.DailyCost> dailyCosts,
        String currency
    ) {}

    public record AccountCost(
            Long accountId,
            String accountName,
            double totalNetCost,
            String currency
    ) {
    }
    
    /**
     * BigQuery 결과를 DailyCost 리스트로 변환 (날짜별로 이미 집계된 값 사용)
     */
    private ParseResult parseDailyCosts(TableResult result) {
        List<GcpAccountDailyCostsResponse.DailyCost> dailyCosts = new ArrayList<>();
        String currency = "USD";
        boolean currencySet = false;
        
        for (var row : result.iterateAll()) {
            String date = row.get("date").getStringValue();
            double grossCost = row.get("gross_cost") != null ? row.get("gross_cost").getDoubleValue() : 0.0;
            double creditUsed = row.get("credit_used_from_credits") != null ? row.get("credit_used_from_credits").getDoubleValue() : 0.0;
            double netCost = roundToFirstDecimal(grossCost - creditUsed);
            
            if (!currencySet && row.get("currency") != null) {
                currency = row.get("currency").getStringValue();
                currencySet = true;
            }
            
            GcpAccountDailyCostsResponse.DailyCost dailyCost = new GcpAccountDailyCostsResponse.DailyCost();
            dailyCost.setDate(date);
            dailyCost.setGrossCost(grossCost);
            dailyCost.setCreditUsed(creditUsed);
            dailyCost.setNetCost(netCost);
            dailyCost.setDisplayNetCost(Math.max(0, netCost));
            dailyCosts.add(dailyCost);
        }
        
        // 날짜순으로 정렬
        dailyCosts.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        
        return new ParseResult(dailyCosts, currency);
    }
    
    /**
     * 값을 소수점 첫째자리로 반올림
     */
    private double roundToFirstDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
    
    /**
     * 월별 비용 정보
     */
    public record MonthlyCost(
            Long accountId,
            String accountName,
            int year,
            int month,
            double totalGrossCost,
            double totalCreditUsed,
            double totalNetCost,
            double displayNetCost, // 표시용 netCost (음수면 0)
            String currency
    ) {}
}

