package com.budgetops.backend.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException() {
        super("결제 정보를 찾을 수 없습니다.");
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
