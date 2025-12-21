package com.budgetops.backend.ai.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.azure.service.AzureComputeService;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.service.GcpResourceService;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import com.budgetops.backend.ncp.service.NcpServerService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceAnalysisService 테스트")
class ResourceAnalysisServiceTest {

    @Mock
    private AwsAccountRepository awsAccountRepository;

    @Mock
    private AwsEc2Service awsEc2Service;

    @Mock
    private AzureAccountRepository azureAccountRepository;

    @Mock
    private AzureComputeService azureComputeService;

    @Mock
    private GcpAccountRepository gcpAccountRepository;

    @Mock
    private GcpResourceService gcpResourceService;

    @Mock
    private NcpAccountRepository ncpAccountRepository;

    @Mock
    private NcpServerService ncpServerService;

    @InjectMocks
    private ResourceAnalysisService resourceAnalysisService;

    @BeforeEach
    void setUp() {
        // 기본적으로 빈 리스트 반환
        given(awsAccountRepository.findByOwnerIdAndActiveTrue(anyLong()))
                .willReturn(Collections.emptyList());
        given(azureAccountRepository.findByOwnerIdAndActiveTrue(anyLong()))
                .willReturn(Collections.emptyList());
        given(gcpAccountRepository.findByOwnerId(anyLong()))
                .willReturn(Collections.emptyList());
        given(ncpAccountRepository.findByOwnerIdAndActiveTrue(anyLong()))
                .willReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("모든 리소스 분석 - 빈 계정")
    void analyzeAllResources_EmptyAccounts() {
        // when
        ResourceAnalysisService.ResourceAnalysisResult result = 
                resourceAnalysisService.analyzeAllResources(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAwsResources()).isEmpty();
        assertThat(result.getAzureResources()).isEmpty();
        assertThat(result.getGcpResources()).isEmpty();
        assertThat(result.getNcpResources()).isEmpty();
    }

    @Test
    @DisplayName("모든 리소스 분석 - AWS 리소스 포함")
    void analyzeAllResources_WithAwsResources() {
        // given
        AwsAccount account = new AwsAccount();
        account.setId(1L);
        account.setName("Test AWS Account");
        account.setDefaultRegion("us-east-1");

        AwsEc2InstanceResponse instance = AwsEc2InstanceResponse.builder()
                .instanceId("i-1234567890abcdef0")
                .instanceType("t3.medium")
                .name("Test Instance")
                .build();

        given(awsAccountRepository.findByOwnerIdAndActiveTrue(1L))
                .willReturn(List.of(account));
        given(awsEc2Service.listInstances(1L, "us-east-1"))
                .willReturn(List.of(instance));

        // when
        ResourceAnalysisService.ResourceAnalysisResult result = 
                resourceAnalysisService.analyzeAllResources(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAwsResources()).isNotEmpty();
    }
}

