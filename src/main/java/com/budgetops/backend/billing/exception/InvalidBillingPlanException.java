package com.budgetops.backend.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBillingPlanException extends RuntimeException {

    public InvalidBillingPlanException(String planName) {
        super("유효하지 않은 요금제입니다: " + planName);
    }

    public InvalidBillingPlanException(String planName, Throwable cause) {
        super("유효하지 않은 요금제입니다: " + planName, cause);
    }
}
