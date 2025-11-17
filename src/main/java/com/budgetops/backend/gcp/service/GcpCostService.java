package com.budgetops.backend.gcp.service;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class GcpCostService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final GcpAccountRepository accountRepository;

    /**
     * GCP 계정의 비용 조회 (일별)
     * 
     * @param accountId GCP 계정 ID
     * @param startDate 시작 날짜 (YYYY-MM-DD 형식)
     * @param endDate 종료 날짜 (YYYY-MM-DD 형식)
     * @return 일별 비용 목록
     */
    @Transactional(readOnly = true)
    public List<DailyCost> getCosts(Long accountId, String startDate, String endDate) {
        GcpAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "GCP 계정을 찾을 수 없습니다."));

        if (account.getBillingAccountId() == null || account.getBillingAccountId().isBlank()) {
            log.warn("GCP 계정 {}에 빌링 계정 ID가 설정되지 않았습니다.", accountId);
            return new ArrayList<>();
        }

        if (account.getBillingExportDatasetId() == null || account.getBillingExportDatasetId().isBlank()) {
            log.warn("GCP 계정 {}에 빌링 내보내기 데이터셋이 설정되지 않았습니다.", accountId);
            return new ArrayList<>();
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
                return new ArrayList<>();
            }

            // BigQuery 쿼리 실행
            String query = buildDailyCostQuery(projectId, datasetId, tableNames, startDate, endDate);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
            TableResult result = bigQuery.query(queryConfig);

            // 결과 파싱
            List<DailyCost> dailyCosts = parseDailyCosts(result);
            
            log.info("Successfully fetched {} days of cost data for account {} (total cost: {})", 
                    dailyCosts.size(), accountId, 
                    dailyCosts.stream().mapToDouble(DailyCost::totalCost).sum());
            
            return dailyCosts;

        } catch (Exception e) {
            log.error("Failed to fetch GCP costs for account {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("GCP 비용 조회 실패: " + e.getMessage(), e);
        }
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
                "  service.description as service, " +
                "  SUM(cost) as cost, " +
                "  SUM(usage.amount) as usage_amount, " +
                "  currency " +
                "FROM `%s.%s.%s` " +
                "WHERE DATE(usage_start_time) >= '%s' " +
                "  AND DATE(usage_start_time) < '%s' " +
                "GROUP BY date, service, currency " +
                "ORDER BY date, service",
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
                "  service.description as service, " +
                "  cost, " +
                "  usage.amount as usage_amount, " +
                "  currency " +
                "FROM `%s.%s.%s` " +
                "WHERE DATE(usage_start_time) >= '%s' " +
                "  AND DATE(usage_start_time) < '%s'",
                projectId, datasetId, tableNames.get(i), startDate, endDate
            ));
        }
        
        // 최종 집계
        return String.format(
            "SELECT " +
            "  date, " +
            "  service, " +
            "  SUM(cost) as cost, " +
            "  SUM(usage_amount) as usage_amount, " +
            "  currency " +
            "FROM (%s) " +
            "GROUP BY date, service, currency " +
            "ORDER BY date, service",
            query.toString()
        );
    }

    /**
     * BigQuery 결과를 DailyCost 리스트로 변환
     */
    private List<DailyCost> parseDailyCosts(TableResult result) {
        Map<String, DailyCostBuilder> dailyCostMap = new HashMap<>();
        
        result.iterateAll().forEach(row -> {
            String date = row.get("date").getStringValue();
            String service = row.get("service") != null ? row.get("service").getStringValue() : "Unknown";
            double cost = row.get("cost") != null ? row.get("cost").getDoubleValue() : 0.0;
            double usageAmount = row.get("usage_amount") != null ? row.get("usage_amount").getDoubleValue() : 0.0;
            String currency = row.get("currency") != null ? row.get("currency").getStringValue() : "USD";
            
            DailyCostBuilder builder = dailyCostMap.computeIfAbsent(date, k -> new DailyCostBuilder(date));
            builder.addService(service, cost, usageAmount, currency);
        });
        
        return dailyCostMap.values().stream()
                .map(DailyCostBuilder::build)
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .toList();
    }

    /**
     * DailyCost 빌더 클래스
     */
    private static class DailyCostBuilder {
        private final String date;
        private double totalCost = 0.0;
        private final List<ServiceCost> services = new ArrayList<>();
        private String currency = "USD";

        DailyCostBuilder(String date) {
            this.date = date;
        }

        void addService(String service, double cost, double usageAmount, String currency) {
            this.totalCost += cost;
            this.currency = currency; // 마지막 통화 사용 (일반적으로 모두 동일)
            services.add(new ServiceCost(service, cost, usageAmount));
        }

        DailyCost build() {
            return new DailyCost(date, totalCost, new ArrayList<>(services), currency);
        }
    }

    // DTO 클래스들
    public record DailyCost(
        String date,
        double totalCost,
        List<ServiceCost> services,
        String currency
    ) {}

    public record ServiceCost(
        String service,
        double cost,
        double usageAmount
    ) {}
}

