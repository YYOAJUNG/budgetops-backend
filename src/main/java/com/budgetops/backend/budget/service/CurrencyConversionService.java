package com.budgetops.backend.budget.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CurrencyConversionService {

    @Value("${app.currency.usd-to-krw:1350}")
    private BigDecimal usdToKrwRate;

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null || fromCurrency == null || toCurrency == null) {
            return BigDecimal.ZERO;
        }
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        if (fromCurrency.equalsIgnoreCase("USD") && toCurrency.equalsIgnoreCase("KRW")) {
            return amount.multiply(usdToKrwRate).setScale(2, RoundingMode.HALF_UP);
        }
        if (fromCurrency.equalsIgnoreCase("KRW") && toCurrency.equalsIgnoreCase("USD")) {
            return amount.divide(usdToKrwRate, 2, RoundingMode.HALF_UP);
        }
        // 지원하지 않는 통화 조합이면 입력 값을 그대로 반환
        return amount;
    }

    public BigDecimal usdToKrw(BigDecimal amount) {
        return convert(amount, "USD", "KRW");
    }
}

