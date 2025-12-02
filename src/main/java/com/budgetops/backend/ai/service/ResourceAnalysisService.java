package com.budgetops.backend.ai.service;

import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsEc2Service;
import com.budgetops.backend.aws.dto.AwsEc2InstanceResponse;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.azure.service.AzureComputeService;
import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.service.GcpResourceService;
import com.budgetops.backend.gcp.dto.GcpResourceResponse;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import com.budgetops.backend.ncp.service.NcpServerService;
import com.budgetops.backend.ncp.dto.NcpServerInstanceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 리소스 기반 분석 서비스
 * 실제 클라우드 리소스 데이터를 수집하고 룰과 매칭하여 최적화 기회를 식별합니다.
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
     * 모든 CSP의 리소스를 분석하고 최적화 기회를 식별합니다.
     * @param memberId 현재 사용자 ID (GCP 리소스 조회에 필요)
     */
    public ResourceAnalysisResult analyzeAllResources(Long memberId) {
        ResourceAnalysisResult result = new ResourceAnalysisResult();
        
        // AWS EC2 분석
        try {
            List<AwsAccount> awsAccounts = awsAccountRepository.findByActiveTrue();
            for (AwsAccount account : awsAccounts) {
                try {
                    String region = account.getDefaultRegion() != null ? account.getDefaultRegion() : "us-east-1";
                    List<AwsEc2InstanceResponse> instances = awsEc2Service.listInstances(account.getId(), region);
                    result.addAwsResources(account.getName(), region, instances);
                } catch (Exception e) {
                    log.warn("Failed to analyze AWS account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to analyze AWS resources", e);
        }
        
        // Azure VM 분석
        try {
            List<AzureAccount> azureAccounts = azureAccountRepository.findByActiveTrue();
            for (AzureAccount account : azureAccounts) {
                try {
                    List<AzureVirtualMachineResponse> vms = azureComputeService.listVirtualMachines(account.getId(), null);
                    result.addAzureResources(account.getName(), vms);
                } catch (Exception e) {
                    log.warn("Failed to analyze Azure account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to analyze Azure resources", e);
        }
        
        // GCP 리소스 분석
        try {
            List<GcpAccount> gcpAccounts = gcpAccountRepository.findAll();
            for (GcpAccount account : gcpAccounts) {
                try {
                    // memberId가 null이면 계정 소유자 ID 사용
                    Long targetMemberId = memberId != null ? memberId : (account.getOwner() != null ? account.getOwner().getId() : null);
                    if (targetMemberId != null) {
                        List<GcpResourceResponse> resources = gcpResourceService.listResources(account.getId(), targetMemberId).getResources();
                        result.addGcpResources(account.getName(), resources);
                    }
                } catch (Exception e) {
                    log.warn("Failed to analyze GCP account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to analyze GCP resources", e);
        }
        
        // NCP 서버 분석
        try {
            List<NcpAccount> ncpAccounts = ncpAccountRepository.findByActiveTrue();
            for (NcpAccount account : ncpAccounts) {
                try {
                    String regionCode = account.getRegionCode() != null ? account.getRegionCode() : "KR";
                    List<NcpServerInstanceResponse> servers = ncpServerService.listInstances(account.getId(), regionCode);
                    result.addNcpResources(account.getName(), regionCode, servers);
                } catch (Exception e) {
                    log.warn("Failed to analyze NCP account {}: {}", account.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to analyze NCP resources", e);
        }
        
        return result;
    }
    
    /**
     * 리소스 분석 결과를 프롬프트용 텍스트로 변환합니다.
     */
    public String formatResourceAnalysisForPrompt(ResourceAnalysisResult analysis) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== 실제 클라우드 리소스 현황 및 최적화 기회 ===\n\n");
        
        // AWS 리소스
        if (!analysis.awsResources.isEmpty()) {
            sb.append("📊 AWS EC2 리소스:\n");
            for (Map.Entry<String, Map<String, List<AwsEc2InstanceResponse>>> accountEntry : analysis.awsResources.entrySet()) {
                String accountName = accountEntry.getKey();
                Map<String, List<AwsEc2InstanceResponse>> regions = accountEntry.getValue();
                
                int totalInstances = regions.values().stream().mapToInt(List::size).sum();
                long runningCount = regions.values().stream()
                        .flatMap(List::stream)
                        .filter(i -> "running".equalsIgnoreCase(i.getState()))
                        .count();
                long stoppedCount = totalInstances - runningCount;
                
                sb.append(String.format("- 계정: %s\n", accountName));
                sb.append(String.format("  총 %d대 (실행중: %d대, 중지: %d대)\n", totalInstances, runningCount, stoppedCount));
                
                // 인스턴스 타입별 통계
                Map<String, Long> typeCount = new HashMap<>();
                for (List<AwsEc2InstanceResponse> instances : regions.values()) {
                    for (AwsEc2InstanceResponse instance : instances) {
                        String type = instance.getInstanceType() != null ? instance.getInstanceType() : "unknown";
                        typeCount.put(type, typeCount.getOrDefault(type, 0L) + 1);
                    }
                }
                if (!typeCount.isEmpty()) {
                    sb.append("  인스턴스 타입: ");
                    List<String> typeSummary = typeCount.entrySet().stream()
                            .map(e -> e.getKey() + " x" + e.getValue())
                            .collect(Collectors.toList());
                    sb.append(String.join(", ", typeSummary)).append("\n");
                }
                
                // 실행 중인 인스턴스 상세 (최적화 기회 식별)
                List<AwsEc2InstanceResponse> runningInstances = regions.values().stream()
                        .flatMap(List::stream)
                        .filter(i -> "running".equalsIgnoreCase(i.getState()))
                        .collect(Collectors.toList());
                
                if (!runningInstances.isEmpty()) {
                    sb.append("  실행 중인 주요 인스턴스:\n");
                    for (AwsEc2InstanceResponse instance : runningInstances.subList(0, Math.min(10, runningInstances.size()))) {
                        sb.append(String.format("    • %s (%s) - %s\n", 
                                instance.getName() != null ? instance.getName() : instance.getInstanceId(),
                                instance.getInstanceType() != null ? instance.getInstanceType() : "unknown",
                                instance.getState()));
                    }
                    if (runningInstances.size() > 10) {
                        sb.append(String.format("    ... 외 %d개\n", runningInstances.size() - 10));
                    }
                }
                sb.append("\n");
            }
        }
        
        // Azure 리소스
        if (!analysis.azureResources.isEmpty()) {
            sb.append("📊 Azure Virtual Machines:\n");
            for (Map.Entry<String, List<AzureVirtualMachineResponse>> accountEntry : analysis.azureResources.entrySet()) {
                String accountName = accountEntry.getKey();
                List<AzureVirtualMachineResponse> vms = accountEntry.getValue();
                
                long runningCount = vms.stream()
                        .filter(vm -> "running".equalsIgnoreCase(vm.getPowerState()))
                        .count();
                long stoppedCount = vms.size() - runningCount;
                
                sb.append(String.format("- 계정: %s\n", accountName));
                sb.append(String.format("  총 %d대 (실행중: %d대, 중지: %d대)\n", vms.size(), runningCount, stoppedCount));
                
                // VM 크기별 통계
                Map<String, Long> sizeCount = new HashMap<>();
                for (AzureVirtualMachineResponse vm : vms) {
                    String size = vm.getVmSize() != null ? vm.getVmSize() : "unknown";
                    sizeCount.put(size, sizeCount.getOrDefault(size, 0L) + 1);
                }
                if (!sizeCount.isEmpty()) {
                    sb.append("  VM 크기: ");
                    List<String> sizeSummary = sizeCount.entrySet().stream()
                            .map(e -> e.getKey() + " x" + e.getValue())
                            .collect(Collectors.toList());
                    sb.append(String.join(", ", sizeSummary)).append("\n");
                }
                
                // 실행 중인 VM 상세
                List<AzureVirtualMachineResponse> runningVms = vms.stream()
                        .filter(vm -> "running".equalsIgnoreCase(vm.getPowerState()))
                        .collect(Collectors.toList());
                
                if (!runningVms.isEmpty()) {
                    sb.append("  실행 중인 주요 VM:\n");
                    for (AzureVirtualMachineResponse vm : runningVms.subList(0, Math.min(10, runningVms.size()))) {
                        sb.append(String.format("    • %s (%s) - %s\n", 
                                vm.getName(),
                                vm.getVmSize() != null ? vm.getVmSize() : "unknown",
                                vm.getPowerState()));
                    }
                    if (runningVms.size() > 10) {
                        sb.append(String.format("    ... 외 %d개\n", runningVms.size() - 10));
                    }
                }
                sb.append("\n");
            }
        }
        
        // GCP 리소스
        if (!analysis.gcpResources.isEmpty()) {
            sb.append("📊 GCP 리소스:\n");
            for (Map.Entry<String, List<GcpResourceResponse>> accountEntry : analysis.gcpResources.entrySet()) {
                String accountName = accountEntry.getKey();
                List<GcpResourceResponse> resources = accountEntry.getValue();
                
                Map<String, Long> typeCount = new HashMap<>();
                for (GcpResourceResponse resource : resources) {
                    String type = resource.getResourceTypeShort() != null ? resource.getResourceTypeShort() : "unknown";
                    typeCount.put(type, typeCount.getOrDefault(type, 0L) + 1);
                }
                
                sb.append(String.format("- 계정: %s\n", accountName));
                sb.append(String.format("  총 %d개 리소스\n", resources.size()));
                if (!typeCount.isEmpty()) {
                    sb.append("  리소스 타입: ");
                    List<String> typeSummary = typeCount.entrySet().stream()
                            .map(e -> e.getKey() + " x" + e.getValue())
                            .collect(Collectors.toList());
                    sb.append(String.join(", ", typeSummary)).append("\n");
                }
                sb.append("\n");
            }
        }
        
        // NCP 리소스
        if (!analysis.ncpResources.isEmpty()) {
            sb.append("📊 NCP 서버:\n");
            for (Map.Entry<String, Map<String, List<NcpServerInstanceResponse>>> accountEntry : analysis.ncpResources.entrySet()) {
                String accountName = accountEntry.getKey();
                Map<String, List<NcpServerInstanceResponse>> regions = accountEntry.getValue();
                
                int totalServers = regions.values().stream().mapToInt(List::size).sum();
                long runningCount = regions.values().stream()
                        .flatMap(List::stream)
                        .filter(s -> "running".equalsIgnoreCase(s.getServerInstanceStatus()))
                        .count();
                
                sb.append(String.format("- 계정: %s\n", accountName));
                sb.append(String.format("  총 %d대 (실행중: %d대)\n", totalServers, runningCount));
                
                // 실행 중인 서버 상세
                List<NcpServerInstanceResponse> runningServers = regions.values().stream()
                        .flatMap(List::stream)
                        .filter(s -> "running".equalsIgnoreCase(s.getServerInstanceStatus()))
                        .collect(Collectors.toList());
                
                if (!runningServers.isEmpty()) {
                    sb.append("  실행 중인 주요 서버:\n");
                    for (NcpServerInstanceResponse server : runningServers.subList(0, Math.min(10, runningServers.size()))) {
                        sb.append(String.format("    • %s (%d vCPU, %dGB RAM) - %s\n", 
                                server.getServerName() != null ? server.getServerName() : server.getServerInstanceNo(),
                                server.getCpuCount() != null ? server.getCpuCount() : 0,
                                server.getMemorySize() != null ? server.getMemorySize() : 0,
                                server.getServerInstanceStatus()));
                    }
                    if (runningServers.size() > 10) {
                        sb.append(String.format("    ... 외 %d개\n", runningServers.size() - 10));
                    }
                }
                sb.append("\n");
            }
        }
        
        if (analysis.awsResources.isEmpty() && analysis.azureResources.isEmpty() 
                && analysis.gcpResources.isEmpty() && analysis.ncpResources.isEmpty()) {
            sb.append("현재 활성화된 클라우드 계정이 없습니다.\n");
            sb.append("계정을 연결하면 실제 리소스 데이터를 기반으로 최적화 조언을 제공할 수 있습니다.\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 리소스 분석 결과를 담는 클래스
     */
    public static class ResourceAnalysisResult {
        public final Map<String, Map<String, List<AwsEc2InstanceResponse>>> awsResources = new HashMap<>();
        public final Map<String, List<AzureVirtualMachineResponse>> azureResources = new HashMap<>();
        public final Map<String, List<GcpResourceResponse>> gcpResources = new HashMap<>();
        public final Map<String, Map<String, List<NcpServerInstanceResponse>>> ncpResources = new HashMap<>();
        
        public void addAwsResources(String accountName, String region, List<AwsEc2InstanceResponse> instances) {
            awsResources.computeIfAbsent(accountName, k -> new HashMap<>()).put(region, instances);
        }
        
        public void addAzureResources(String accountName, List<AzureVirtualMachineResponse> vms) {
            azureResources.put(accountName, vms);
        }
        
        public void addGcpResources(String accountName, List<GcpResourceResponse> resources) {
            gcpResources.put(accountName, resources);
        }
        
        public void addNcpResources(String accountName, String region, List<NcpServerInstanceResponse> servers) {
            ncpResources.computeIfAbsent(accountName, k -> new HashMap<>()).put(region, servers);
        }
    }
}

