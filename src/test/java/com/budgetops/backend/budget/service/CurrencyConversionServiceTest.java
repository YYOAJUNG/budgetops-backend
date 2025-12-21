package com.budgetops.backend.budget.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyConversion Service 테스트")
class CurrencyConversionServiceTest {

    @InjectMocks
    private CurrencyConversionService currencyConversionService;

    @BeforeEach
    void setUp() {
        // 기본 환율 설정 (USD -> KRW: 1350)
        ReflectionTestUtils.setField(currencyConversionService, "usdToKrwRate", new BigDecimal("1350"));
    }

    @Test
    @DisplayName("USD to KRW 변환")
    void convert_UsdToKrw() {
        // given
        BigDecimal amount = new BigDecimal("100");
        String fromCurrency = "USD";
        String toCurrency = "KRW";

        // when
        BigDecimal result = currencyConversionService.convert(amount, fromCurrency, toCurrency);

        // then
        assertThat(result).isEqualByComparingTo(new BigDecimal("135000.00"));
    }

    @Test
    @DisplayName("KRW to USD 변환")
    void convert_KrwToUsd() {
        // given
        BigDecimal amount = new BigDecimal("135000");
        String fromCurrency = "KRW";
        String toCurrency = "USD";

        // when
        BigDecimal result = currencyConversionService.convert(amount, fromCurrency, toCurrency);

        // then
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("같은 통화 변환 - USD to USD")
    void convert_SameCurrency() {
        // given
        BigDecimal amount = new BigDecimal("100");
        String fromCurrency = "USD";
        String toCurrency = "USD";

        // when
        BigDecimal result = currencyConversionService.convert(amount, fromCurrency, toCurrency);

        // then
        assertThat(result).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("같은 통화 변환 - KRW to KRW")
    void convert_SameCurrencyKrw() {
        // given
        BigDecimal amount = new BigDecimal("100000");
        String fromCurrency = "KRW";
        String toCurrency = "KRW";

        // when
        BigDecimal result = currencyConversionService.convert(amount, fromCurrency, toCurrency);

        // then
        assertThat(result).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("null 값 처리")
    void convert_NullValues() {
        // when & then
        assertThat(currencyConversionService.convert(null, "USD", "KRW"))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(currencyConversionService.convert(new BigDecimal("100"), null, "KRW"))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(currencyConversionService.convert(new BigDecimal("100"), "USD", null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("지원하지 않는 통화 조합 - 입력값 그대로 반환")
    void convert_UnsupportedCurrency() {
        // given
        BigDecimal amount = new BigDecimal("100");
        String fromCurrency = "EUR";
        String toCurrency = "JPY";

        // when
        BigDecimal result = currencyConversionService.convert(amount, fromCurrency, toCurrency);

        // then
        assertThat(result).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("usdToKrw 헬퍼 메서드")
    void usdToKrw() {
        // given
        BigDecimal amount = new BigDecimal("50");

        // when
        BigDecimal result = currencyConversionService.usdToKrw(amount);

        // then
        assertThat(result).isEqualByComparingTo(new BigDecimal("67500.00"));
    }

    @Test
    @DisplayName("소수점 처리 - USD to KRW")
    void convert_DecimalPrecision() {
        // given
        BigDecimal amount = new BigDecimal("1.5");
        String fromCurrency = "USD";
        String toCurrency = "KRW";

        // when
        BigDecimal result = currencyConversionService.convert(amount, fromCurrency, toCurrency);

        // then
        assertThat(result).isEqualByComparingTo(new BigDecimal("2025.00"));
    }

    @Test
    @DisplayName("소수점 처리 - KRW to USD")
    void convert_DecimalPrecisionKrwToUsd() {
        // given
        BigDecimal amount = new BigDecimal("2025");
        String fromCurrency = "KRW";
        String toCurrency = "USD";

        // when
        BigDecimal result = currencyConversionService.convert(amount, fromCurrency, toCurrency);

        // then
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.50"));
    }
}

