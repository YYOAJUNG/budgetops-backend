package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsEc2Alert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwsAlert Service 테스트")
class AwsAlertServiceTest {

    @Mock
    private AwsEc2AlertService ec2AlertService;

    @InjectMocks
    private AwsAlertService awsAlertService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("checkAllServices - 모든 서비스 알림 체크 성공")
    void checkAllServices_Success() {
        // given
        AwsEc2Alert mockAlert = AwsEc2Alert.builder()
                .accountId(1L)
                .instanceId("i-123456")
                .message("Test alert")
                .build();
        
        given(ec2AlertService.checkAllAccounts()).willReturn(List.of(mockAlert));

        // when
        List<AwsEc2Alert> alerts = awsAlertService.checkAllServices();

        // then
        assertThat(alerts).hasSize(1);
        verify(ec2AlertService).checkAllAccounts();
    }

    @Test
    @DisplayName("checkAllServices - EC2 서비스 체크 실패 시에도 계속 진행")
    void checkAllServices_EC2ServiceFails() {
        // given
        given(ec2AlertService.checkAllAccounts()).willThrow(new RuntimeException("EC2 체크 실패"));

        // when
        List<AwsEc2Alert> alerts = awsAlertService.checkAllServices();

        // then
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("checkAllServicesForAccount - 특정 계정의 모든 서비스 알림 체크")
    void checkAllServicesForAccount_Success() {
        // given
        AwsEc2Alert mockAlert = AwsEc2Alert.builder()
                .accountId(100L)
                .instanceId("i-123456")
                .message("Test alert")
                .build();
        
        given(ec2AlertService.checkAccount(100L)).willReturn(List.of(mockAlert));

        // when
        List<AwsEc2Alert> alerts = awsAlertService.checkAllServicesForAccount(100L);

        // then
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAccountId()).isEqualTo(100L);
        verify(ec2AlertService).checkAccount(100L);
    }

    @Test
    @DisplayName("checkAllServicesForAccount - 알림이 없는 경우")
    void checkAllServicesForAccount_NoAlerts() {
        // given
        given(ec2AlertService.checkAccount(100L)).willReturn(Collections.emptyList());

        // when
        List<AwsEc2Alert> alerts = awsAlertService.checkAllServicesForAccount(100L);

        // then
        assertThat(alerts).isEmpty();
    }
}

