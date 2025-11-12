package com.budgetops.backend.azure.service;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class AzureCostService {

    public List<DailyCost> getCosts(Long accountId, String startDate, String endDate) {
        log.debug("AzureCostService#getCosts stub - accountId={}, start={}, end={}", accountId, startDate, endDate);
        return Collections.emptyList();
    }

    public MonthlyCost getMonthlyCost(Long accountId, int year, int month) {
        log.debug("AzureCostService#getMonthlyCost stub - accountId={}, year={}, month={}", accountId, year, month);
        return MonthlyCost.builder()
                .accountId(accountId)
                .currency("USD")
                .amount(0.0)
                .build();
    }

    public List<AccountCost> getAllAccountsCosts(String startDate, String endDate) {
        log.debug("AzureCostService#getAllAccountsCosts stub - start={}, end={}", startDate, endDate);
        return Collections.emptyList();
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

