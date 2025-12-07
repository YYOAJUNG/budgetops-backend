package com.budgetops.backend.billing.scheduler;

import com.budgetops.backend.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 빌링 관련 스케줄러
 * - 만료된 구독을 FREE 플랜으로 다운그레이드
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingScheduler {

    private final BillingService billingService;

    /**
     * 매일 자정에 만료된 구독을 FREE 플랜으로 다운그레이드
     * cron: 초 분 시 일 월 요일
     * 0 0 0 * * * = 매일 자정
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void downgradeExpiredSubscriptions() {
        log.info("만료된 구독 다운그레이드 스케줄러 시작");
        try {
            billingService.downgradeExpiredSubscriptions();
            log.info("만료된 구독 다운그레이드 스케줄러 완료");
        } catch (Exception e) {
            log.error("만료된 구독 다운그레이드 스케줄러 실패: {}", e.getMessage(), e);
        }
    }
}
