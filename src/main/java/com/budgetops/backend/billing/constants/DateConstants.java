package com.budgetops.backend.billing.constants;

import java.time.format.DateTimeFormatter;

/**
 * 날짜 포맷 상수
 */
public final class DateConstants {

    private DateConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * 날짜 포맷: yyyy-MM-dd
     * Frontend API 응답용
     */
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 날짜시간 포맷: yyyy-MM-dd HH:mm:ss
     * 상세 로그 및 트랜잭션 기록용
     */
    public static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
}
