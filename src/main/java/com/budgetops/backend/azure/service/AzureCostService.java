package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureCostService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AzureAccountRepository accountRepository;
    private final AzureApiClient apiClient;
    private final AzureTokenManager tokenManager;
    private final AzureCostRateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public List<DailyCost> getCosts(Long accountId, Long memberId, String startDate, String endDate) {
        AzureAccount account = getAccount(accountId, memberId);
        AzureAccessToken token = tokenManager.getToken(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
        rateLimiter.awaitAllowance(account.getSubscriptionId());
        try {
            JsonNode node = apiClient.queryCosts(account.getSubscriptionId(), token.getAccessToken(), startDate, endDate, "Daily");
            return parseDailyCosts(account, node);
        } catch (Exception e) {
            tokenManager.invalidate(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public MonthlyCost getMonthlyCost(Long accountId, Long memberId, int year, int month) {
        AzureAccount account = getAccount(accountId, memberId);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        AzureAccessToken token = tokenManager.getToken(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
        rateLimiter.awaitAllowance(account.getSubscriptionId());
        JsonNode node;
        try {
            node = apiClient.queryCosts(
                    account.getSubscriptionId(),
                    token.getAccessToken(),
                    from.format(ISO_DATE),
                    to.plusDays(1).format(ISO_DATE),
                    "None"
            );
        } catch (Exception e) {
            tokenManager.invalidate(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
            throw e;
        }

        double amount = extractTotalCost(node);
        String currency = extractCurrency(node).orElse("USD");

        return MonthlyCost.builder()
                .accountId(accountId)
                .amount(amount)
                .currency(currency)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AccountCost> getAllAccountsCosts(Long memberId, String startDate, String endDate) {
        return accountRepository.findByWorkspaceOwnerIdAndActiveTrue(memberId).stream()
                .map(account -> {
                    try {
                        AzureAccessToken token = tokenManager.getToken(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
                        rateLimiter.awaitAllowance(account.getSubscriptionId());
                        JsonNode node = apiClient.queryCosts(account.getSubscriptionId(), token.getAccessToken(), startDate, endDate, "None");
                        double amount = extractTotalCost(node);
                        String currency = extractCurrency(node).orElse("USD");

                        return AccountCost.builder()
                                .accountId(account.getId())
                                .accountName(account.getName())
                                .amount(amount)
                                .currency(currency)
                                .build();
                    } catch (Exception e) {
                        log.warn("비용 조회 실패 - Azure 계정 id={}: {}", account.getId(), e.getMessage());
                        tokenManager.invalidate(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
                        return AccountCost.builder()
                                .accountId(account.getId())
                                .accountName(account.getName())
                                .amount(0.0)
                                .currency("USD")
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    private AzureAccount getAccount(Long accountId, Long memberId) {
        AzureAccount account = accountRepository.findByIdAndWorkspaceOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Azure 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 Azure 계정입니다.");
        }
        return account;
    }

    private List<DailyCost> parseDailyCosts(AzureAccount account, JsonNode node) {
        JsonNode properties = node.path("properties");
        JsonNode columns = properties.path("columns");
        JsonNode rows = properties.path("rows");

        if (rows.isMissingNode() || !rows.isArray()) {
            return Collections.emptyList();
        }

        Map<String, Integer> columnIndex = mapColumns(columns);

        int dateIdx = columnIndex.getOrDefault("UsageDate", -1);
        if (dateIdx == -1) {
            dateIdx = columnIndex.getOrDefault("UsageDateTime", -1);
        }
        int costIdx = columnIndex.getOrDefault("Cost", -1);
        int currencyIdx = columnIndex.getOrDefault("Currency", -1);

        List<DailyCost> result = new ArrayList<>();
        for (JsonNode row : rows) {
            String date = dateIdx >= 0 && row.has(dateIdx) ? row.get(dateIdx).asText() : "";
            double amount = costIdx >= 0 && row.has(costIdx) ? row.get(costIdx).asDouble(0.0) : 0.0;
            String currency = currencyIdx >= 0 && row.has(currencyIdx) ? row.get(currencyIdx).asText("USD") : "USD";

            result.add(DailyCost.builder()
                    .accountId(account.getId())
                    .date(date)
                    .amount(amount)
                    .currency(currency)
                    .build());
        }
        return result;
    }

    private double extractTotalCost(JsonNode node) {
        JsonNode properties = node.path("properties");
        JsonNode columns = properties.path("columns");
        JsonNode rows = properties.path("rows");
        if (rows.isMissingNode() || rows.size() == 0) {
            return 0.0;
        }
        Map<String, Integer> columnIndex = mapColumns(columns);
        int costIdx = columnIndex.getOrDefault("Cost", -1);
        if (costIdx == -1) {
            return 0.0;
        }
        JsonNode firstRow = rows.get(0);
        if (costIdx >= firstRow.size()) {
            return 0.0;
        }
        JsonNode value = firstRow.get(costIdx);
        return value != null && value.isNumber() ? value.asDouble(0.0) : 0.0;
    }

    private Optional<String> extractCurrency(JsonNode node) {
        JsonNode properties = node.path("properties");
        JsonNode columns = properties.path("columns");
        JsonNode rows = properties.path("rows");
        if (rows.isMissingNode() || rows.size() == 0) {
            return Optional.empty();
        }
        Map<String, Integer> columnIndex = mapColumns(columns);
        int currencyIdx = columnIndex.getOrDefault("Currency", -1);
        if (currencyIdx == -1) {
            return Optional.empty();
        }
        JsonNode firstRow = rows.get(0);
        if (currencyIdx >= firstRow.size()) {
            return Optional.empty();
        }
        JsonNode value = firstRow.get(currencyIdx);
        return value != null && value.isTextual() ? Optional.of(value.asText()) : Optional.empty();
    }

    private Map<String, Integer> mapColumns(JsonNode columns) {
        Map<String, Integer> columnIndex = new HashMap<>();
        if (columns != null && columns.isArray()) {
            for (int i = 0; i < columns.size(); i++) {
                JsonNode col = columns.get(i);
                columnIndex.put(col.path("name").asText("col" + i), i);
            }
        }
        return columnIndex;
    }

    @Value
    @Builder
    public static class DailyCost {
        Long accountId;
        String date;
        double amount;
        String currency;
    }

    @Value
    @Builder
    public static class MonthlyCost {
        Long accountId;
        double amount;
        String currency;
    }

    @Value
    @Builder
    public static class AccountCost {
        Long accountId;
        String accountName;
        double amount;
        String currency;
    }
}

