package com.budgetops.backend.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class PaymentRequiredException extends RuntimeException {

    public PaymentRequiredException() {
        super("PRO 요금제로 변경하려면 결제 정보가 필요합니다. 결제 정보를 먼저 등록해주세요.");
    }

    public PaymentRequiredException(String message) {
        super(message);
    }
}
