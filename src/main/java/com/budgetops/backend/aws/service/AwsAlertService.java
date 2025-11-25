package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AWS 통합 알림 서비스
 * EC2, RDS, S3 등 모든 AWS 서비스의 알림을 통합 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AwsAlertService {
    
    private final AwsEc2AlertService ec2AlertService;
    // TODO: RDS, S3 등 다른 서비스 알림 서비스 추가 예정
    
    /**
     * 모든 AWS 서비스의 알림을 통합하여 반환
     */
    public List<AwsEc2Alert> checkAllServices() {
        log.info("Checking alerts for all AWS services");
        List<AwsEc2Alert> allAlerts = new ArrayList<>();
        
        try {
            // EC2 알림
            List<AwsEc2Alert> ec2Alerts = ec2AlertService.checkAllAccounts();
            allAlerts.addAll(ec2Alerts);
            log.info("EC2 alerts: {}", ec2Alerts.size());
        } catch (Exception e) {
            log.error("Failed to check EC2 alerts", e);
        }
        
        // TODO: RDS 알림 추가
        // TODO: S3 알림 추가
        // TODO: Lambda 알림 추가
        // TODO: DynamoDB 알림 추가
        
        log.info("Total AWS alerts: {}", allAlerts.size());
        return allAlerts;
    }
    
    /**
     * 특정 계정의 모든 서비스 알림 체크
     */
    public List<AwsEc2Alert> checkAllServicesForAccount(Long accountId) {
        log.info("Checking alerts for all AWS services for account {}", accountId);
        List<AwsEc2Alert> allAlerts = new ArrayList<>();
        
        try {
            // EC2 알림
            List<AwsEc2Alert> ec2Alerts = ec2AlertService.checkAccount(accountId);
            allAlerts.addAll(ec2Alerts);
        } catch (Exception e) {
            log.error("Failed to check EC2 alerts for account {}", accountId, e);
        }
        
        // TODO: 다른 서비스 알림 추가
        
        log.info("Total AWS alerts for account {}: {}", accountId, allAlerts.size());
        return allAlerts;
    }
}

