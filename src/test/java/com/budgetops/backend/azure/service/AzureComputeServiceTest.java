package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureComputeServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private AzureAccountRepository repository;

    @Mock
    private AzureApiClient apiClient;

    @Mock
    private AzureTokenManager tokenManager;

    @InjectMocks
    private AzureComputeService service;

    @Test
    @DisplayName("VM 목록 조회는 위치 필터를 적용하고 응답을 변환한다")
    void listVirtualMachines_success() throws Exception {
        AzureAccount account = new AzureAccount();
        account.setId(1L);
        account.setActive(Boolean.TRUE);
        account.setSubscriptionId("sub-1");
        account.setTenantId("tenant-1");
        account.setClientId("client-1");
        account.setClientSecretEnc("secret-1");

        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(tokenManager.getToken("tenant-1", "client-1", "secret-1"))
                .thenReturn(AzureAccessToken.builder()
                        .accessToken("token")
                        .tokenType("Bearer")
                        .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                        .build());

        JsonNode response = OBJECT_MAPPER.readTree("""
                {
                  "value": [
                    {
                      "id": "/subscriptions/sub-1/resourceGroups/rg/providers/Microsoft.Compute/virtualMachines/vm1",
                      "name": "vm1",
                      "location": "korea-central",
                      "properties": {
                        "hardwareProfile": {"vmSize": "Standard_B2s"},
                        "provisioningState": "Succeeded",
                        "instanceView": {
                          "statuses": [
                            {"code": "PowerState/running"}
                          ]
                        },
                        "storageProfile": {
                          "osDisk": {"osType": "Linux"}
                        },
                        "osProfile": {
                          "computerName": "vm1"
                        }
                      }
                    },
                    {
                      "id": "/subscriptions/sub-1/resourceGroups/rg/providers/Microsoft.Compute/virtualMachines/vm2",
                      "name": "vm2",
                      "location": "eastus",
                      "properties": {}
                    }
                  ]
                }
                """);

        when(apiClient.listVirtualMachines(eq("sub-1"), anyString())).thenReturn(response);

        var result = service.listVirtualMachines(1L, "korea-central");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("vm1");
        assertThat(result.get(0).getPowerState()).isEqualTo("running");
    }

    @Test
    @DisplayName("비활성 계정으로 호출하면 예외가 발생한다")
    void listVirtualMachines_inactiveAccount() {
        AzureAccount account = new AzureAccount();
        account.setId(1L);
        account.setActive(Boolean.FALSE);

        when(repository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.listVirtualMachines(1L, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("API 호출 실패 시 토큰 캐시를 무효화한다")
    void listVirtualMachines_apiErrorInvalidatesToken() {
        AzureAccount account = new AzureAccount();
        account.setId(1L);
        account.setActive(Boolean.TRUE);
        account.setSubscriptionId("sub-1");
        account.setTenantId("tenant-1");
        account.setClientId("client-1");
        account.setClientSecretEnc("secret-1");

        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(tokenManager.getToken("tenant-1", "client-1", "secret-1")).thenReturn(
                AzureAccessToken.builder()
                        .accessToken("token")
                        .tokenType("Bearer")
                        .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                        .build()
        );

        when(apiClient.listVirtualMachines(eq("sub-1"), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.listVirtualMachines(1L, null))
                .isInstanceOf(IllegalStateException.class);

        verify(tokenManager).invalidate("tenant-1", "client-1", "secret-1");
    }
}

