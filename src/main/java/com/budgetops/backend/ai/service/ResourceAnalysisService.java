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
import com.budgetops.backend.aws.dto.AwsEc2MetricsResponse;
import com.budgetops.backend.ncp.dto.NcpServerMetricsResponse;
import com.budgetops.backend.gcp.dto.GcpResourceMetricsResponse;
import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.service.AzureTokenManager;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final AzureApiClient azureApiClient;
    private final AzureTokenManager azureTokenManager;
    
    public ResourceAnalysisService(
            AwsAccountRepository awsAccountRepository,
            AwsEc2Service awsEc2Service,
            AzureAccountRepository azureAccountRepository,
            AzureComputeService azureComputeService,
            GcpAccountRepository gcpAccountRepository,
            GcpResourceService gcpResourceService,
            NcpAccountRepository ncpAccountRepository,
            NcpServerService ncpServerService,
            AzureApiClient azureApiClient,
            AzureTokenManager azureTokenManager) {
        this.awsAccountRepository = awsAccountRepository;
        this.awsEc2Service = awsEc2Service;
        this.azureAccountRepository = azureAccountRepository;
        this.azureComputeService = azureComputeService;
        this.gcpAccountRepository = gcpAccountRepository;
        this.gcpResourceService = gcpResourceService;
        this.ncpAccountRepository = ncpAccountRepository;
        this.ncpServerService = ncpServerService;
        this.azureApiClient = azureApiClient;
        this.azureTokenManager = azureTokenManager;
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
     * @param analysis 리소스 분석 결과
     * @param memberId 현재 사용자 ID (GCP 메트릭 조회에 필요)
     */
    public String formatResourceAnalysisForPrompt(ResourceAnalysisResult analysis, Long memberId) {
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
                
                // 실행 중인 인스턴스 상세 (메트릭 포함)
                List<AwsEc2InstanceResponse> runningInstances = regions.values().stream()
                        .flatMap(List::stream)
                        .filter(i -> "running".equalsIgnoreCase(i.getState()))
                        .collect(Collectors.toList());
                
                if (!runningInstances.isEmpty()) {
                    sb.append("  실행 중인 주요 인스턴스 (최근 7일 메트릭 포함):\n");
                    for (AwsEc2InstanceResponse instance : runningInstances.subList(0, Math.min(10, runningInstances.size()))) {
                        String instanceInfo = String.format("    • %s (%s)", 
                                instance.getName() != null ? instance.getName() : instance.getInstanceId(),
                                instance.getInstanceType() != null ? instance.getInstanceType() : "unknown");
                        
                        // 메트릭 조회 시도 (실패해도 계속 진행)
                        try {
                            // AWS 계정 찾기
                            String region = regions.entrySet().stream()
                                    .filter(e -> e.getValue().contains(instance))
                                    .map(Map.Entry::getKey)
                                    .findFirst()
                                    .orElse(null);
                            
                            if (region != null) {
                                List<AwsAccount> awsAccounts = awsAccountRepository.findByActiveTrue();
                                AwsAccount account = awsAccounts.stream()
                                        .filter(acc -> accountName.equals(acc.getName()))
                                        .findFirst()
                                        .orElse(null);
                                
                                if (account != null) {
                                    // AWS EC2 메트릭 조회 (7일간 = 168시간)
                                    AwsEc2MetricsResponse metrics = awsEc2Service.getInstanceMetrics(
                                            account.getId(), 
                                            instance.getInstanceId(), 
                                            region, 
                                            168);
                                    
                                    // CPU 사용률 평균 계산
                                    double avgCpu = metrics.getCpuUtilization().stream()
                                            .mapToDouble(m -> m.getValue() != null ? m.getValue() : 0.0)
                                            .average()
                                            .orElse(0.0);
                                    
                                    if (avgCpu > 0) {
                                        instanceInfo += String.format(" - CPU: %.1f%%", avgCpu);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to fetch metrics for AWS EC2 instance {}: {}", instance.getInstanceId(), e.getMessage());
                        }
                        
                        sb.append(instanceInfo).append("\n");
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
                
                // 실행 중인 VM 상세 (메트릭 포함)
                List<AzureVirtualMachineResponse> runningVms = vms.stream()
                        .filter(vm -> "running".equalsIgnoreCase(vm.getPowerState()))
                        .collect(Collectors.toList());
                
                if (!runningVms.isEmpty()) {
                    sb.append("  실행 중인 주요 VM (최근 7일 메트릭 포함):\n");
                    
                    // Azure 계정 찾기
                    AzureAccount azureAccount = null;
                    try {
                        List<AzureAccount> accounts = azureAccountRepository.findByActiveTrue();
                        azureAccount = accounts.stream()
                                .filter(acc -> accountName.equals(acc.getName()))
                                .findFirst()
                                .orElse(null);
                    } catch (Exception e) {
                        log.debug("Failed to find Azure account for metrics: {}", e.getMessage());
                    }
                    
                    for (AzureVirtualMachineResponse vm : runningVms.subList(0, Math.min(10, runningVms.size()))) {
                        String vmInfo = String.format("    • %s (%s)", 
                                vm.getName(),
                                vm.getVmSize() != null ? vm.getVmSize() : "unknown");
                        
                        // 메트릭 조회 시도 (실패해도 계속 진행)
                        try {
                            if (azureAccount != null && vm.getResourceGroup() != null && !vm.getResourceGroup().isEmpty()) {
                                // Azure VM 메트릭 조회 (7일간 = 168시간)
                                AzureAccessToken token = azureTokenManager.getToken(
                                        azureAccount.getTenantId(), 
                                        azureAccount.getClientId(), 
                                        azureAccount.getClientSecretEnc());
                                
                                JsonNode metricsResponse = azureApiClient.getVirtualMachineMetrics(
                                        azureAccount.getSubscriptionId(),
                                        vm.getResourceGroup(),
                                        vm.getName(),
                                        token.getAccessToken(),
                                        168);
                                
                                // CPU 및 메모리 사용률 계산
                                double avgCpu = calculateAverageMetric(metricsResponse, "Percentage CPU");
                                double avgMemory = calculateMemoryUtilization(metricsResponse, "Available Memory Bytes");
                                
                                if (avgCpu > 0) {
                                    vmInfo += String.format(" - CPU: %.1f%%", avgCpu);
                                }
                                if (avgMemory > 0) {
                                    vmInfo += String.format(", 메모리 사용률: %.1f%%", avgMemory);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to fetch metrics for Azure VM {}: {}", vm.getName(), e.getMessage());
                        }
                        
                        sb.append(vmInfo).append("\n");
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
                
                // Compute Engine 인스턴스만 필터링하여 메트릭 조회
                List<GcpResourceResponse> computeInstances = resources.stream()
                        .filter(r -> "compute.googleapis.com/Instance".equals(r.getResourceType()))
                        .filter(r -> "RUNNING".equalsIgnoreCase(r.getStatus()) || "running".equalsIgnoreCase(r.getStatus()))
                        .collect(Collectors.toList());
                
                if (!computeInstances.isEmpty()) {
                    sb.append("  실행 중인 Compute Engine 인스턴스 (최근 7일 메트릭 포함):\n");
                    for (GcpResourceResponse resource : computeInstances.subList(0, Math.min(10, computeInstances.size()))) {
                        String resourceInfo = String.format("    • %s (%s)", 
                                resource.getResourceName() != null ? resource.getResourceName() : resource.getResourceId(),
                                resource.getResourceTypeShort() != null ? resource.getResourceTypeShort() : "unknown");
                        
                        // 메트릭 조회 시도 (실패해도 계속 진행)
                        try {
                            // GCP 리소스 메트릭 조회 (7일간 = 168시간)
                            GcpResourceMetricsResponse metrics = gcpResourceService.getResourceMetrics(
                                    resource.getResourceId(),
                                    memberId,
                                    168);
                            
                            // CPU 사용률 평균 계산
                            double avgCpu = metrics.getCpuUtilization().stream()
                                    .mapToDouble(m -> m.getValue() != null ? m.getValue() : 0.0)
                                    .average()
                                    .orElse(0.0);
                            
                            // 메모리 사용률 평균 계산 (Monitoring Agent가 설치된 경우에만 사용 가능)
                            double avgMemory = metrics.getMemoryUtilization().stream()
                                    .mapToDouble(m -> m.getValue() != null ? m.getValue() : 0.0)
                                    .average()
                                    .orElse(0.0);
                            
                            if (avgCpu > 0) {
                                resourceInfo += String.format(" - CPU: %.1f%%", avgCpu);
                            }
                            if (avgMemory > 0) {
                                resourceInfo += String.format(", 메모리: %.1f%%", avgMemory);
                            }
                        } catch (Exception e) {
                            log.debug("Failed to fetch metrics for GCP resource {}: {}", resource.getResourceId(), e.getMessage());
                        }
                        
                        sb.append(resourceInfo).append("\n");
                    }
                    if (computeInstances.size() > 10) {
                        sb.append(String.format("    ... 외 %d개\n", computeInstances.size() - 10));
                    }
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
                    sb.append("  실행 중인 주요 서버 (최근 7일 메트릭 포함):\n");
                    
                    // NCP 계정 찾기
                    NcpAccount ncpAccount = null;
                    try {
                        List<NcpAccount> accounts = ncpAccountRepository.findByActiveTrue();
                        ncpAccount = accounts.stream()
                                .filter(acc -> accountName.equals(acc.getName()))
                                .findFirst()
                                .orElse(null);
                    } catch (Exception e) {
                        log.debug("Failed to find NCP account for metrics: {}", e.getMessage());
                    }
                    
                    for (NcpServerInstanceResponse server : runningServers.subList(0, Math.min(10, runningServers.size()))) {
                        String serverInfo = String.format("    • %s (%d vCPU, %dGB RAM)", 
                                server.getServerName() != null ? server.getServerName() : server.getServerInstanceNo(),
                                server.getCpuCount() != null ? server.getCpuCount() : 0,
                                server.getMemorySize() != null ? server.getMemorySize() : 0);
                        
                        // 메트릭 조회 시도 (실패해도 계속 진행)
                        try {
                            if (ncpAccount != null) {
                                String regionCode = regions.keySet().stream().findFirst().orElse(null);
                                if (regionCode == null) {
                                    regionCode = ncpAccount.getRegionCode();
                                }
                                
                                // NCP 서버 메트릭 조회 (7일간 = 168시간)
                                NcpServerMetricsResponse metrics = ncpServerService.getInstanceMetrics(
                                        ncpAccount.getId(),
                                        server.getServerInstanceNo(),
                                        regionCode,
                                        168);
                                
                                // CPU 사용률 평균 계산
                                double avgCpu = metrics.getCpuUtilization().stream()
                                        .mapToDouble(m -> m.getValue() != null ? m.getValue() : 0.0)
                                        .average()
                                        .orElse(0.0);
                                
                                if (avgCpu > 0) {
                                    serverInfo += String.format(" - CPU: %.1f%%", avgCpu);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to fetch metrics for NCP server {}: {}", server.getServerInstanceNo(), e.getMessage());
                        }
                        
                        sb.append(serverInfo).append("\n");
                    }
                    if (runningServers.size() > 10) {
                        sb.append(String.format("    ... 외 %d개\n", runningServers.size() - 10));
                    }
                }
                sb.append("\n");
            }
        }
        
        if (analysis.awsResources.isEmpty() && analysis.azureResources.isEmpty() && 
            analysis.gcpResources.isEmpty() && analysis.ncpResources.isEmpty()) {
            sb.append("현재 연결된 클라우드 계정이 없습니다. 계정을 연결하면 실제 리소스 데이터를 기반으로 최적화 조언을 제공할 수 있습니다.\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Azure 메트릭 응답에서 특정 메트릭의 평균값 계산
     */
    private double calculateAverageMetric(JsonNode metricsResponse, String metricName) {
        try {
            JsonNode value = metricsResponse.path("value");
            if (!value.isArray()) {
                return 0.0;
            }
            
            for (JsonNode metric : value) {
                JsonNode name = metric.path("name");
                if (name.path("value").asText("").equals(metricName)) {
                    JsonNode timeseries = metric.path("timeseries");
                    if (!timeseries.isArray() || timeseries.size() == 0) {
                        return 0.0;
                    }
                    
                    JsonNode data = timeseries.get(0).path("data");
                    if (!data.isArray()) {
                        return 0.0;
                    }
                    
                    double sum = 0.0;
                    int count = 0;
                    for (JsonNode point : data) {
                        JsonNode average = point.path("average");
                        if (!average.isMissingNode()) {
                            sum += average.asDouble(0.0);
                            count++;
                        }
                    }
                    
                    return count > 0 ? sum / count : 0.0;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to calculate average metric {}: {}", metricName, e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Azure 메트릭 응답에서 메모리 사용률 계산
     * Available Memory Bytes를 사용하여 메모리 사용률을 계산합니다.
     * (VM 크기에 따라 총 메모리가 다르므로, 정확한 계산을 위해서는 VM 크기 정보가 필요하지만,
     * 여기서는 간단히 Available Memory가 적을수록 사용률이 높다는 것을 나타냅니다)
     */
    private double calculateMemoryUtilization(JsonNode metricsResponse, String metricName) {
        // 메모리 사용률은 Available Memory Bytes만으로는 정확히 계산하기 어렵습니다.
        // VM 크기에 따라 총 메모리가 다르기 때문입니다.
        // 여기서는 간단히 메트릭이 있는지만 확인합니다.
        double avgAvailableMemory = calculateAverageMetric(metricsResponse, metricName);
        // Available Memory가 작을수록 메모리 사용률이 높다는 것을 나타내지만,
        // 정확한 퍼센트 계산을 위해서는 VM 크기 정보가 필요합니다.
        // 일단 메트릭이 있는 경우에만 표시하도록 합니다.
        return avgAvailableMemory > 0 ? 0.0 : 0.0; // 정확한 계산을 위해서는 VM 크기 정보 필요
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

