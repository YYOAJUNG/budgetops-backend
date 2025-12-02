package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
import com.budgetops.backend.azure.dto.AzureVmMetricsResponse;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureComputeService {

    private final AzureAccountRepository accountRepository;
    private final AzureApiClient apiClient;
    private final AzureTokenManager tokenManager;

    @Transactional(readOnly = true)
    public List<AzureVirtualMachineResponse> listVirtualMachines(Long accountId, String locationFilter) {
        AzureAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Azure 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 Azure 계정입니다.");
        }

        AzureAccessToken token = tokenManager.getToken(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
        JsonNode response;
        try {
            response = apiClient.listVirtualMachines(account.getSubscriptionId(), token.getAccessToken());
        } catch (Exception e) {
            tokenManager.invalidate(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
            throw e;
        }
        JsonNode value = response.path("value");

        List<AzureVirtualMachineResponse> result = new ArrayList<>();
        if (value.isMissingNode() || !value.isArray()) {
            return result;
        }

        String normalizedFilter = locationFilter != null ? locationFilter.toLowerCase(Locale.ROOT) : null;

        for (JsonNode vmNode : value) {
            try {
                String location = vmNode.path("location").asText("");
                if (normalizedFilter != null && !location.toLowerCase(Locale.ROOT).equals(normalizedFilter)) {
                    continue;
                }

                JsonNode properties = vmNode.path("properties");
                if (properties.isMissingNode()) {
                    log.warn("VM properties missing for VM: {}", vmNode.path("id").asText(""));
                    continue;
                }

                String vmId = vmNode.path("id").asText("");
                String vmName = vmNode.path("name").asText("");

                JsonNode instanceView = fetchInstanceView(
                        account.getSubscriptionId(),
                        vmId,
                        vmName,
                        token.getAccessToken()
                );
                NetworkInfo networkInfo = fetchNetworkInfo(
                        account.getSubscriptionId(),
                        vmNode,
                        token.getAccessToken()
                );

                AzureVirtualMachineResponse vm = AzureVirtualMachineResponse.builder()
                        .id(vmId)
                        .name(vmName)
                        .resourceGroup(extractResourceGroup(vmId))
                        .location(location)
                        .vmSize(extractVmSize(properties))
                        .provisioningState(properties.path("provisioningState").asText(""))
                        .powerState(extractPowerState(instanceView))
                        .osType(extractOsType(properties))
                        .computerName(extractComputerName(properties))
                        .privateIp(networkInfo.privateIp())
                        .publicIp(networkInfo.publicIp())
                        .availabilityZone(extractAvailabilityZone(vmNode))
                        .timeCreated(extractTimeCreated(properties))
                        .build();

                result.add(vm);
            } catch (Exception e) {
                log.error("Failed to parse VM node: {}", vmNode.path("id").asText(""), e);
                // 개별 VM 파싱 실패 시에도 계속 진행
            }
        }

        return result;
    }

    private String extractResourceGroup(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return "";
        }
        String[] parts = resourceId.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("resourceGroups".equalsIgnoreCase(parts[i])) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractPowerState(JsonNode instanceView) {
        if (instanceView == null || instanceView.isMissingNode()) {
            return "";
        }
        
        JsonNode statuses = instanceView.path("statuses");
        if (!statuses.isArray()) {
            return "";
        }
        for (JsonNode status : statuses) {
            String code = status.path("code").asText("");
            if (code.startsWith("PowerState/")) {
                return code.substring("PowerState/".length());
            }
        }
        return "";
    }

    private JsonNode fetchInstanceView(String subscriptionId, String vmId, String vmName, String accessToken) {
        if (vmId == null || vmId.isBlank() || vmName == null || vmName.isBlank()) {
            return null;
        }
        String resourceGroup = extractResourceGroup(vmId);
        if (resourceGroup.isBlank()) {
            log.warn("Failed to determine resourceGroup for VM: {}", vmId);
            return null;
        }

        try {
            return apiClient.getVirtualMachineInstanceView(subscriptionId, resourceGroup, vmName, accessToken);
        } catch (Exception e) {
            log.warn("Failed to fetch instance view for VM {}: {}", vmId, e.getMessage());
            return null;
        }
    }

    private String extractVmSize(JsonNode properties) {
        JsonNode hardwareProfile = properties.path("hardwareProfile");
        if (hardwareProfile.isMissingNode()) {
            return "";
        }
        return hardwareProfile.path("vmSize").asText("");
    }

    private String extractOsType(JsonNode properties) {
        JsonNode storageProfile = properties.path("storageProfile");
        if (storageProfile.isMissingNode()) {
            return "";
        }
        JsonNode osDisk = storageProfile.path("osDisk");
        if (osDisk.isMissingNode()) {
            return "";
        }
        return osDisk.path("osType").asText("");
    }

    private String extractComputerName(JsonNode properties) {
        JsonNode osProfile = properties.path("osProfile");
        if (osProfile.isMissingNode()) {
            return "";
        }
        return osProfile.path("computerName").asText("");
    }

    private String extractAvailabilityZone(JsonNode vmNode) {
        JsonNode zones = vmNode.path("zones");
        if (zones.isArray() && zones.size() > 0) {
            return zones.get(0).asText("");
        }
        JsonNode zone = vmNode.path("properties").path("availabilityZone");
        if (!zone.isMissingNode()) {
            return zone.asText("");
        }
        return "";
    }

    private String extractTimeCreated(JsonNode properties) {
        return properties.path("timeCreated").asText("");
    }

    private NetworkInfo fetchNetworkInfo(String subscriptionId, JsonNode vmNode, String accessToken) {
        JsonNode networkInterfaces = vmNode.path("properties").path("networkProfile").path("networkInterfaces");
        if (!networkInterfaces.isArray() || networkInterfaces.isEmpty()) {
            return NetworkInfo.empty();
        }
        JsonNode primaryNic = null;
        for (JsonNode nic : networkInterfaces) {
            if (nic.path("properties").path("primary").asBoolean(false)) {
                primaryNic = nic;
                break;
            }
        }
        if (primaryNic == null) {
            primaryNic = networkInterfaces.get(0);
        }
        String nicId = primaryNic.path("id").asText("");
        if (nicId.isBlank()) {
            return NetworkInfo.empty();
        }
        String resourceGroup = extractResourceGroup(nicId);
        String nicName = extractResourceName(nicId);
        if (resourceGroup.isBlank() || nicName.isBlank()) {
            return NetworkInfo.empty();
        }

        try {
            JsonNode nicNode = apiClient.getNetworkInterface(subscriptionId, resourceGroup, nicName, accessToken);
            JsonNode ipConfigs = nicNode.path("properties").path("ipConfigurations");
            if (!ipConfigs.isArray() || ipConfigs.isEmpty()) {
                return NetworkInfo.empty();
            }
            JsonNode primaryConfig = null;
            for (JsonNode cfg : ipConfigs) {
                if (cfg.path("properties").path("primary").asBoolean(false)) {
                    primaryConfig = cfg;
                    break;
                }
            }
            if (primaryConfig == null) {
                primaryConfig = ipConfigs.get(0);
            }

            String privateIp = primaryConfig.path("properties").path("privateIPAddress").asText("");
            String publicIp = "";

            JsonNode publicIpRef = primaryConfig.path("properties").path("publicIPAddress");
            if (!publicIpRef.isMissingNode()) {
                String publicIpId = publicIpRef.path("id").asText("");
                if (!publicIpId.isBlank()) {
                    String publicIpRg = extractResourceGroup(publicIpId);
                    String publicIpName = extractResourceName(publicIpId);
                    if (!publicIpName.isBlank()) {
                        try {
                            JsonNode publicIpNode = apiClient.getPublicIpAddress(subscriptionId, publicIpRg, publicIpName, accessToken);
                            publicIp = publicIpNode.path("properties").path("ipAddress").asText("");
                        } catch (Exception e) {
                            log.warn("Failed to fetch public IP {}: {}", publicIpName, e.getMessage());
                        }
                    }
                }
            }

            return new NetworkInfo(privateIp, publicIp);
        } catch (Exception e) {
            log.warn("Failed to fetch network interface for VM {}: {}", vmNode.path("id").asText(""), e.getMessage());
            return NetworkInfo.empty();
        }
    }

    private String extractResourceName(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return "";
        }
        String[] parts = resourceId.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : "";
    }

    @Transactional(readOnly = true)
    public AzureVmMetricsResponse getVirtualMachineMetrics(Long accountId, String vmName, String resourceGroup, Integer hours) {
        AzureAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Azure 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 Azure 계정입니다.");
        }

        if (resourceGroup == null || resourceGroup.isBlank()) {
            throw new IllegalArgumentException("Resource group이 필요합니다.");
        }

        AzureAccessToken token = tokenManager.getToken(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
        JsonNode metricsResponse;
        try {
            metricsResponse = apiClient.getVirtualMachineMetrics(
                    account.getSubscriptionId(),
                    resourceGroup,
                    vmName,
                    token.getAccessToken(),
                    hours
            );
        } catch (Exception e) {
            tokenManager.invalidate(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
            log.error("Failed to fetch Azure VM metrics for {}: {}", vmName, e.getMessage(), e);
            throw new IllegalStateException("Azure VM 메트릭 조회 실패: " + e.getMessage(), e);
        }

        return parseMetricsResponse(metricsResponse, vmName, resourceGroup);
    }

    private AzureVmMetricsResponse parseMetricsResponse(JsonNode response, String vmName, String resourceGroup) {
        List<AzureVmMetricsResponse.MetricDataPoint> cpuMetrics = new ArrayList<>();
        List<AzureVmMetricsResponse.MetricDataPoint> networkInMetrics = new ArrayList<>();
        List<AzureVmMetricsResponse.MetricDataPoint> networkOutMetrics = new ArrayList<>();
        List<AzureVmMetricsResponse.MetricDataPoint> memoryMetrics = new ArrayList<>();

        JsonNode value = response.path("value");
        if (!value.isArray()) {
            log.warn("Azure metrics response does not contain value array");
            return AzureVmMetricsResponse.builder()
                    .vmName(vmName)
                    .resourceGroup(resourceGroup)
                    .cpuUtilization(cpuMetrics)
                    .networkIn(networkInMetrics)
                    .networkOut(networkOutMetrics)
                    .memoryUtilization(memoryMetrics)
                    .build();
        }

        for (JsonNode metric : value) {
            String metricName = metric.path("name").path("value").asText("");
            JsonNode timeseries = metric.path("timeseries");
            if (!timeseries.isArray() || timeseries.size() == 0) {
                continue;
            }

            JsonNode data = timeseries.get(0).path("data");
            if (!data.isArray()) {
                continue;
            }

            List<AzureVmMetricsResponse.MetricDataPoint> targetList = null;
            String unit = metric.path("unit").asText("");

            switch (metricName) {
                case "Percentage CPU":
                    targetList = cpuMetrics;
                    unit = "Percent";
                    break;
                case "Network In Total":
                    targetList = networkInMetrics;
                    unit = "Bytes";
                    break;
                case "Network Out Total":
                    targetList = networkOutMetrics;
                    unit = "Bytes";
                    break;
                case "Available Memory Percentage":
                case "Available Memory Bytes":
                    targetList = memoryMetrics;
                    if ("Available Memory Bytes".equals(metricName)) {
                        unit = "Bytes";
                    } else {
                        unit = "Percent";
                    }
                    break;
                default:
                    continue;
            }

            if (targetList != null) {
                for (JsonNode dataPoint : data) {
                    String timestamp = dataPoint.path("timeStamp").asText("");
                    JsonNode average = dataPoint.path("average");
                    Double metricValue = average.isMissingNode() ? null : average.asDouble(0.0);

                    if (timestamp != null && !timestamp.isBlank() && metricValue != null) {
                        targetList.add(AzureVmMetricsResponse.MetricDataPoint.builder()
                                .timestamp(timestamp)
                                .value(metricValue)
                                .unit(unit)
                                .build());
                    }
                }
            }
        }

        return AzureVmMetricsResponse.builder()
                .vmName(vmName)
                .resourceGroup(resourceGroup)
                .cpuUtilization(cpuMetrics)
                .networkIn(networkInMetrics)
                .networkOut(networkOutMetrics)
                .memoryUtilization(memoryMetrics)
                .build();
    }

    private record NetworkInfo(String privateIp, String publicIp) {
        static NetworkInfo empty() {
            return new NetworkInfo("", "");
        }
    }
}

