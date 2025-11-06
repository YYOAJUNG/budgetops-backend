package com.budgetops.backend.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PaymentVerificationException extends RuntimeException {

    public PaymentVerificationException(String impUid) {
        super("결제 정보를 찾을 수 없습니다: " + impUid);
    }

    public PaymentVerificationException(String impUid, String status) {
        super("결제 상태가 'paid'가 아닙니다. impUid: " + impUid + ", 현재 상태: " + status);
    }

    public PaymentVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
