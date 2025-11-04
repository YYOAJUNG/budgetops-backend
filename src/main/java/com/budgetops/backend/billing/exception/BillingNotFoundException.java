package com.budgetops.backend.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BillingNotFoundException extends RuntimeException {

    public BillingNotFoundException() {
        super("요금제 정보를 찾을 수 없습니다.");
    }

    public BillingNotFoundException(Long memberId) {
        super("해당 사용자의 요금제 정보를 찾을 수 없습니다: " + memberId);
    }

    public BillingNotFoundException(String message) {
        super(message);
    }
}
