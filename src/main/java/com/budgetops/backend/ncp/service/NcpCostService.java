package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.client.NcpApiClient;
import com.budgetops.backend.ncp.dto.NcpCostSummary;
import com.budgetops.backend.ncp.dto.NcpDailyUsage;
import com.budgetops.backend.ncp.dto.NcpMonthlyCost;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class NcpCostService {

    private static final String RESPONSE_FORMAT_JSON = "json";
    private static final String ERROR_MSG_ACCOUNT_NOT_FOUND = "NCP 계정을 찾을 수 없습니다.";
    private static final String ERROR_MSG_INACTIVE_ACCOUNT = "비활성화된 계정입니다.";

    private final NcpAccountRepository accountRepository;
    private final NcpApiClient apiClient;

    /**
     * NCP 계정의 월별 비용 조회
     *
     * @param accountId NCP 계정 ID
     * @param startMonth 시작 월 (YYYYMM 형식, 예: 202401)
     * @param endMonth 종료 월 (YYYYMM 형식, 예: 202403)
     * @return 월별 비용 목록
     */
    public List<NcpMonthlyCost> getCosts(Long accountId, Long memberId, String startMonth, String endMonth) {
        NcpAccount account = validateAndGetAccount(accountId, memberId);
        log.info("Fetching NCP costs for account {} from {} to {}", accountId, startMonth, endMonth);

        try {
            Map<String, String> params = buildMonthlyParams(startMonth, endMonth);
            JsonNode response = apiClient.callBillingApi(
                    "/cost/getContractDemandCostList",
                    params,
                    account.getAccessKey(),
                    account.getSecretKeyEnc()
            );

            List<NcpMonthlyCost> costs = parseContractDemandCostList(response);
            log.info("Found {} cost records for account {} from {} to {}", costs.size(), accountId, startMonth, endMonth);
            return costs;

        } catch (Exception e) {
            log.error("Failed to fetch costs for account {} from {} to {}: {}", accountId, startMonth, endMonth, e.getMessage(), e);
            throw new RuntimeException("비용 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * NCP 계정의 월별 비용 요약 조회
     *
     * @param accountId NCP 계정 ID
     * @param month 조회 월 (YYYYMM 형식, 예: 202401)
     * @return 비용 요약 정보
     */
    public NcpCostSummary getCostSummary(Long accountId, Long memberId, String month) {
        List<NcpMonthlyCost> costs = getCosts(accountId, memberId, month, month);

        CostAggregation aggregation = aggregateCosts(costs);

        log.info("Cost summary for account {} month {}: total={}, discount={}",
                accountId, month, aggregation.totalDemandAmount, aggregation.totalDiscount);

        return NcpCostSummary.builder()
                .month(month)
                .totalCost(aggregation.totalDemandAmount)
                .currency(aggregation.currency)
                .totalDemandAmount(aggregation.totalDemandAmount)
                .totalUseAmount(aggregation.totalUseAmount)
                .totalDiscountAmount(aggregation.totalDiscount)
                .build();
    }

    /**
     * 비용 목록을 집계
     *
     * @param costs 비용 목록
     * @return 집계 결과
     */
    private CostAggregation aggregateCosts(List<NcpMonthlyCost> costs) {
        double totalDemandAmount = 0.0;
        double totalUseAmount = 0.0;
        double totalPromotionDiscount = 0.0;
        double totalEtcDiscount = 0.0;
        String currency = "KRW";

        for (NcpMonthlyCost cost : costs) {
            totalDemandAmount += getAmountOrZero(cost.getDemandAmount());
            totalUseAmount += getAmountOrZero(cost.getUseAmount());
            totalPromotionDiscount += getAmountOrZero(cost.getPromotionDiscountAmount());
            totalEtcDiscount += getAmountOrZero(cost.getEtcDiscountAmount());

            if (cost.getCurrency() != null && !cost.getCurrency().isEmpty()) {
                currency = cost.getCurrency();
            }
        }

        return new CostAggregation(
                totalDemandAmount,
                totalUseAmount,
                totalPromotionDiscount + totalEtcDiscount,
                currency
        );
    }

    /**
     * null-safe 금액 반환
     */
    private double getAmountOrZero(Double amount) {
        return amount != null ? amount : 0.0;
    }

    /**
     * 비용 집계 결과를 담는 내부 클래스
     */
    private static class CostAggregation {
        final double totalDemandAmount;
        final double totalUseAmount;
        final double totalDiscount;
        final String currency;

        CostAggregation(double totalDemandAmount, double totalUseAmount, double totalDiscount, String currency) {
            this.totalDemandAmount = totalDemandAmount;
            this.totalUseAmount = totalUseAmount;
            this.totalDiscount = totalDiscount;
            this.currency = currency;
        }
    }

    /**
     * NCP 계정의 일별 사용량 조회
     *
     * @param accountId NCP 계정 ID
     * @param startDay 시작 일 (YYYYMMDD 형식, 예: 20240101)
     * @param endDay 종료 일 (YYYYMMDD 형식, 예: 20240131)
     * @return 일별 사용량 목록
     */
    public List<NcpDailyUsage> getDailyUsage(Long accountId, Long memberId, String startDay, String endDay) {
        NcpAccount account = validateAndGetAccount(accountId, memberId);
        log.info("Fetching NCP daily usage for account {} from {} to {}", accountId, startDay, endDay);

        try {
            Map<String, String> params = buildDailyParams(startDay, endDay);
            JsonNode response = apiClient.callBillingApi(
                    "/cost/getContractUsageListByDaily",
                    params,
                    account.getAccessKey(),
                    account.getSecretKeyEnc()
            );

            List<NcpDailyUsage> usageList = parseContractUsageListByDaily(response);
            log.info("Found {} daily usage records for account {} from {} to {}", usageList.size(), accountId, startDay, endDay);
            return usageList;

        } catch (Exception e) {
            log.error("Failed to fetch daily usage for account {} from {} to {}: {}", accountId, startDay, endDay, e.getMessage(), e);
            throw new RuntimeException("일별 사용량 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 계정 검증 및 조회
     */
    private NcpAccount validateAndGetAccount(Long accountId, Long memberId) {
        NcpAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ERROR_MSG_ACCOUNT_NOT_FOUND));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException(ERROR_MSG_INACTIVE_ACCOUNT);
        }

        return account;
    }

    /**
     * 특정 회원의 NCP 계정 총 비용 (단일 월 기준)
     */
    public double getMemberMonthlyCost(Long memberId, String month) {
        List<NcpAccount> accounts = accountRepository.findByOwnerIdAndActiveTrue(memberId);
        if (accounts.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (NcpAccount account : accounts) {
            try {
                NcpCostSummary summary = getCostSummary(account.getId(), memberId, month);
                total += summary.getTotalCost() != null ? summary.getTotalCost() : 0.0;
            } catch (Exception e) {
                log.warn("Failed to fetch NCP cost for account {}: {}", account.getId(), e.getMessage());
            }
        }
        return total;
    }

    /**
     * 월별 조회 파라미터 생성
     */
    private Map<String, String> buildMonthlyParams(String startMonth, String endMonth) {
        Map<String, String> params = new HashMap<>();
        params.put("startMonth", startMonth);
        params.put("endMonth", endMonth);
        params.put("responseFormatType", RESPONSE_FORMAT_JSON);
        return params;
    }

    /**
     * 일별 조회 파라미터 생성
     */
    private Map<String, String> buildDailyParams(String startDay, String endDay) {
        Map<String, String> params = new HashMap<>();
        params.put("useStartDay", startDay);
        params.put("useEndDay", endDay);
        params.put("responseFormatType", RESPONSE_FORMAT_JSON);
        return params;
    }

    /**
     * JSON 응답을 파싱하여 일별 사용량 목록 반환
     */
    private List<NcpDailyUsage> parseContractUsageListByDaily(JsonNode response) {
        List<NcpDailyUsage> usageList = new ArrayList<>();

        JsonNode responseNode = response.path("getContractUsageListByDailyResponse");

        if (responseNode.has("contractUsageListByDaily")) {
            JsonNode dailyList = responseNode.get("contractUsageListByDaily");
            if (dailyList.isArray()) {
                for (JsonNode usage : dailyList) {
                    usageList.add(parseDailyUsage(usage));
                }
            }
        }

        return usageList;
    }

    /**
     * 단일 일별 사용량 파싱
     */
    private NcpDailyUsage parseDailyUsage(JsonNode usage) {
        String useDate = formatDateToYYYYMMDD(usage.path("useDate").path("useStartDate").asText(""));

        return NcpDailyUsage.builder()
                .useDate(useDate)
                .contractNo(getTextOrNull(usage, "contract", "contractNo"))
                .instanceName(getTextOrNull(usage, "contract", "instanceName"))
                .contractType(getCodeNameOrNull(usage, "contract", "contractType"))
                .productItemKind(getCodeNameOrNull(usage, "contractProduct", "productItemKind"))
                .usageQuantity(usage.path("usage").path("usageQuantity").asDouble(0.0))
                .unit(getCodeNameOrNull(usage, "usage", "unit"))
                .regionCode(getTextOrNull(usage, "contract", "regionCode"))
                .build();
    }

    /**
     * ISO 날짜 문자열을 YYYYMMDD 형식으로 변환
     */
    private String formatDateToYYYYMMDD(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) {
            return "";
        }
        return isoDate.substring(0, 10).replace("-", "");
    }

    /**
     * JsonNode에서 중첩된 텍스트 값 추출 (null-safe)
     */
    private String getTextOrNull(JsonNode node, String... paths) {
        JsonNode current = node;
        for (String path : paths) {
            current = current.path(path);
            if (current.isMissingNode()) {
                return null;
            }
        }
        return current.asText(null);
    }

    /**
     * JsonNode에서 codeName 추출 (null-safe)
     */
    private String getCodeNameOrNull(JsonNode node, String... paths) {
        JsonNode current = node;
        for (String path : paths) {
            current = current.path(path);
            if (current.isMissingNode()) {
                return null;
            }
        }
        return current.path("codeName").asText(null);
    }

    /**
     * JSON 응답을 파싱하여 월별 비용 목록 반환
     */
    private List<NcpMonthlyCost> parseContractDemandCostList(JsonNode response) {
        List<NcpMonthlyCost> costs = new ArrayList<>();

        JsonNode responseNode = response.path("getContractDemandCostListResponse");

        if (responseNode.has("contractDemandCostList")) {
            JsonNode costList = responseNode.get("contractDemandCostList");
            if (costList.isArray()) {
                for (JsonNode cost : costList) {
                    costs.add(parseMonthlyCost(cost));
                }
            }
        }

        return costs;
    }

    /**
     * 단일 월별 비용 파싱
     */
    private NcpMonthlyCost parseMonthlyCost(JsonNode cost) {
        return NcpMonthlyCost.builder()
                .demandMonth(cost.path("demandMonth").asText(null))
                .demandType(getCodeNameOrNull(cost, "demandType"))
                .demandTypeDetail(getCodeNameOrNull(cost, "demandTypeDetail"))
                .contractNo(getTextOrNull(cost, "contract", "contractNo"))
                .instanceName(getTextOrNull(cost, "contract", "instanceName"))
                .demandAmount(cost.path("demandAmount").asDouble(0.0))
                .useAmount(cost.path("useAmount").asDouble(0.0))
                .promotionDiscountAmount(cost.path("promotionDiscountAmount").asDouble(0.0))
                .etcDiscountAmount(cost.path("etcDiscountAmount").asDouble(0.0))
                .currency(getCodeNameOrNull(cost, "payCurrency"))
                .build();
    }
}
