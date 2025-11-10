package com.budgetops.backend.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(Long memberId) {
        super("사용자를 찾을 수 없습니다: " + memberId);
    }

    public MemberNotFoundException(String message) {
        super(message);
    }
}
