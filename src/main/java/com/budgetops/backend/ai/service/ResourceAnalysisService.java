package com.budgetops.backend.ai.service;

import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.azure.service.AzureComputeService;
import com.budgetops.backend.gcp.dto.GcpResourceResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.service.GcpResourceService;
import com.budgetops.backend.ncp.dto.NcpServerInstanceResponse;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import com.budgetops.backend.ncp.service.NcpServerService;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 리소스 분석 서비스
 * 모든 CSP의 리소스와 메트릭을 조회하여 분석 결과를 제공합니다.
 */
@Slf4j
@Service
public class ResourceAnalysisService {

    private final AwsAccountRepository awsAccountRepository;
    private final AwsEc2Service awsEc2Service;
    private final AzureAccountRepository azureAccountRepository;
    private final AzureComputeService azureComputeService;
    private final GcpAccountRepository gcpAccountRepository;
    private final GcpResourceService gcpResourceService;
    private final NcpAccountRepository ncpAccountRepository;
    private final NcpServerService ncpServerService;

    public ResourceAnalysisService(
            AwsAccountRepository awsAccountRepository,
            AwsEc2Service awsEc2Service,
            AzureAccountRepository azureAccountRepository,
            AzureComputeService azureComputeService,
            GcpAccountRepository gcpAccountRepository,
            GcpResourceService gcpResourceService,
            NcpAccountRepository ncpAccountRepository,
            NcpServerService ncpServerService) {
        this.awsAccountRepository = awsAccountRepository;
        this.awsEc2Service = awsEc2Service;
        this.azureAccountRepository = azureAccountRepository;
        this.azureComputeService = azureComputeService;
        this.gcpAccountRepository = gcpAccountRepository;
        this.gcpResourceService = gcpResourceService;
        this.ncpAccountRepository = ncpAccountRepository;
        this.ncpServerService = ncpServerService;
    }

    /**
     * 모든 CSP의 리소스를 분석하여 결과를 반환합니다.
     */
    public ResourceAnalysisResult analyzeAllResources(Long memberId) {
        ResourceAnalysisResult.ResourceAnalysisResultBuilder builder = ResourceAnalysisResult.builder();

        // AWS 리소스 분석
        Map<String, Map<String, List<AwsEc2InstanceResponse>>> awsResources = new HashMap<>();
        try {
            List<AwsAccount> awsAccounts = awsAccountRepository.findByOwnerIdAndActiveTrue(memberId);
            for (AwsAccount account : awsAccounts) {
                String accountName = account.getName() != null ? account.getName() : "Account " + account.getId();
                String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                try {
                    List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                    awsResources.computeIfAbsent(accountName, k -> new HashMap<>()).put(region, instances);
                } catch (Exception e) {
                    log.warn("Failed to fetch AWS EC2 instances for account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to analyze AWS resources: {}", e.getMessage());
        }
        builder.awsResources(awsResources);

        // Azure 리소스 분석
        Map<String, List<AzureVirtualMachineResponse>> azureResources = new HashMap<>();
        try {
            List<AzureAccount> azureAccounts = azureAccountRepository.findByOwnerIdAndActiveTrue(memberId);
            for (AzureAccount account : azureAccounts) {
                String accountName = account.getName() != null ? account.getName() : "Account " + account.getId();
                try {
                    List<AzureVirtualMachineResponse> vms = azureComputeService.listVirtualMachines(account.getId(), null);
                    azureResources.put(accountName, vms);
                } catch (Exception e) {
                    log.warn("Failed to fetch Azure VMs for account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to analyze Azure resources: {}", e.getMessage());
        }
        builder.azureResources(azureResources);

        // GCP 리소스 분석
        Map<String, List<GcpResourceResponse>> gcpResources = new HashMap<>();
        try {
            List<GcpAccount> gcpAccounts = gcpAccountRepository.findByOwnerId(memberId);
            for (GcpAccount account : gcpAccounts) {
                String accountName = account.getName() != null ? account.getName() : "Account " + account.getId();
                try {
                    var accountResources = gcpResourceService.listAllAccountsResources(memberId);
                    List<GcpResourceResponse> resources = accountResources.stream()
                            .flatMap(ar -> ar.getResources().stream())
                            .collect(Collectors.toList());
                    if (!resources.isEmpty()) {
                        gcpResources.put(accountName, resources);
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch GCP resources for account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to analyze GCP resources: {}", e.getMessage());
        }
        builder.gcpResources(gcpResources);

        // NCP 리소스 분석
        Map<String, Map<String, List<NcpServerInstanceResponse>>> ncpResources = new HashMap<>();
        try {
            List<NcpAccount> ncpAccounts = ncpAccountRepository.findByOwnerIdAndActiveTrue(memberId);
            for (NcpAccount account : ncpAccounts) {
                String accountName = account.getName() != null ? account.getName() : "Account " + account.getId();
                String regionCode = account.getRegionCode() != null ? account.getRegionCode() : "KR";
                try {
                    List<NcpServerInstanceResponse> servers = ncpServerService.listInstances(account.getId(), regionCode);
                    ncpResources.computeIfAbsent(accountName, k -> new HashMap<>()).put(regionCode, servers);
                } catch (Exception e) {
                    log.warn("Failed to fetch NCP servers for account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to analyze NCP resources: {}", e.getMessage());
        }
        builder.ncpResources(ncpResources);

        return builder.build();
    }

    @Value
    @Builder
    public static class ResourceAnalysisResult {
        Map<String, Map<String, List<AwsEc2InstanceResponse>>> awsResources;
        Map<String, List<AzureVirtualMachineResponse>> azureResources;
        Map<String, List<GcpResourceResponse>> gcpResources;
        Map<String, Map<String, List<NcpServerInstanceResponse>>> ncpResources;
    }
}

