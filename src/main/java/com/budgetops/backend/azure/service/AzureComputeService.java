package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.budgetops.backend.azure.dto.AzureVirtualMachineResponse;
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
            String location = vmNode.path("location").asText("");
            if (normalizedFilter != null && !location.toLowerCase(Locale.ROOT).equals(normalizedFilter)) {
                continue;
            }

            JsonNode properties = vmNode.path("properties");
            String vmId = vmNode.path("id").asText("");
            String vmName = vmNode.path("name").asText("");

            AzureVirtualMachineResponse vm = AzureVirtualMachineResponse.builder()
                    .id(vmId)
                    .name(vmName)
                    .resourceGroup(extractResourceGroup(vmId))
                    .location(location)
                    .vmSize(properties.path("hardwareProfile").path("vmSize").asText(""))
                    .provisioningState(properties.path("provisioningState").asText(""))
                    .powerState(extractPowerState(properties))
                    .osType(properties.path("storageProfile").path("osDisk").path("osType").asText(""))
                    .computerName(properties.path("osProfile").path("computerName").asText(""))
                    .privateIp(extractPrivateIp(properties))
                    .publicIp(extractPublicIp(properties))
                    .build();

            result.add(vm);
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

    private String extractPowerState(JsonNode properties) {
        JsonNode statuses = properties.path("instanceView").path("statuses");
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

    private String extractPrivateIp(JsonNode properties) {
        JsonNode networkProfile = properties.path("networkProfile").path("networkInterfaces");
        if (!networkProfile.isArray() || networkProfile.isEmpty()) {
            return "";
        }
        // Azure VM instanceView에는 IP 정보가 노출되지 않으므로 빈 문자열 반환
        // 향후 Network Interface API를 호출하여 보완 가능
        return "";
    }

    private String extractPublicIp(JsonNode properties) {
        // 위 설명과 동일하게 Public IP는 별도 API 호출이 필요하다.
        return "";
    }
}

