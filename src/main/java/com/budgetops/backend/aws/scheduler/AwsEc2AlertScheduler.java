package com.budgetops.backend.aws.scheduler;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import com.budgetops.backend.aws.service.AwsEc2AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AWS EC2 알림 스케줄러
 * 주기적으로 임계치를 확인하고 알림을 발송합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AwsEc2AlertScheduler {
    
    private final AwsEc2AlertService alertService;
    
    /**
     * 매일 오전 9시에 실행
     * 모든 활성 AWS 계정의 EC2 인스턴스 임계치 확인
     */
    @Scheduled(cron = "0 0 9 * * *") // 매일 오전 9시
    public void checkThresholdsDaily() {
        log.info("Starting scheduled AWS EC2 threshold check");
        
        try {
            List<AwsEc2Alert> alerts = alertService.checkAllAccounts();
            log.info("Scheduled check completed: {} alerts generated", alerts.size());
        } catch (Exception e) {
            log.error("Failed to execute scheduled threshold check", e);
        }
    }
    
    /**
     * 매 6시간마다 실행
     * 더 빈번한 모니터링이 필요한 경우
     */
    @Scheduled(cron = "0 0 */6 * * *") // 6시간마다
    public void checkThresholdsPeriodically() {
        log.info("Starting periodic AWS EC2 threshold check");
        
        try {
            List<AwsEc2Alert> alerts = alertService.checkAllAccounts();
            log.info("Periodic check completed: {} alerts generated", alerts.size());
        } catch (Exception e) {
            log.error("Failed to execute periodic threshold check", e);
        }
    }
}

