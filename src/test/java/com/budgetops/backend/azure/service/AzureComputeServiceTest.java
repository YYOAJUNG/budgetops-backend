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
                      "zones": ["1"],
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
                        },
                        "networkProfile": {
                          "networkInterfaces": [
                            {
                              "id": "/subscriptions/sub-1/resourceGroups/rg/providers/Microsoft.Network/networkInterfaces/nic1",
                              "properties": {
                                "primary": true
                              }
                            }
                          ]
                        },
                        "timeCreated": "2025-11-10T10:00:00Z"
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
        when(apiClient.getVirtualMachineInstanceView(eq("sub-1"), eq("rg"), eq("vm1"), anyString()))
                .thenReturn(OBJECT_MAPPER.readTree("""
                        {
                          "statuses": [
                            {"code": "PowerState/running"}
                          ]
                        }
                        """));
        when(apiClient.getNetworkInterface(eq("sub-1"), eq("rg"), eq("nic1"), anyString()))
                .thenReturn(OBJECT_MAPPER.readTree("""
                        {
                          "properties": {
                            "ipConfigurations": [
                              {
                                "properties": {
                                  "privateIPAddress": "10.0.0.4",
                                  "primary": true,
                                  "publicIPAddress": {
                                    "id": "/subscriptions/sub-1/resourceGroups/rg/providers/Microsoft.Network/publicIPAddresses/pip1"
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """));
        when(apiClient.getPublicIpAddress(eq("sub-1"), eq("rg"), eq("pip1"), anyString()))
                .thenReturn(OBJECT_MAPPER.readTree("""
                        {
                          "properties": {
                            "ipAddress": "52.10.10.10"
                          }
                        }
                        """));

        var result = service.listVirtualMachines(1L, "korea-central");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("vm1");
        assertThat(result.get(0).getPowerState()).isEqualTo("running");
        assertThat(result.get(0).getPrivateIp()).isEqualTo("10.0.0.4");
        assertThat(result.get(0).getPublicIp()).isEqualTo("52.10.10.10");
        assertThat(result.get(0).getAvailabilityZone()).isEqualTo("1");
        assertThat(result.get(0).getTimeCreated()).isEqualTo("2025-11-10T10:00:00Z");
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

    @Test
    @DisplayName("가상 머신 시작 요청을 전송한다")
    void startVirtualMachine_success() {
        AzureAccount account = new AzureAccount();
        account.setId(1L);
        account.setActive(Boolean.TRUE);
        account.setSubscriptionId("sub-1");
        account.setTenantId("tenant-1");
        account.setClientId("client-1");
        account.setClientSecretEnc("secret");

        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(tokenManager.getToken("tenant-1", "client-1", "secret"))
                .thenReturn(AzureAccessToken.builder()
                        .accessToken("token")
                        .tokenType("Bearer")
                        .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                        .build());

        service.startVirtualMachine(1L, "vm1", "rg");

        verify(apiClient).startVirtualMachine("sub-1", "rg", "vm1", "token");
    }

    @Test
    @DisplayName("가상 머신 정지 요청을 전송한다")
    void stopVirtualMachine_success() {
        AzureAccount account = new AzureAccount();
        account.setId(1L);
        account.setActive(Boolean.TRUE);
        account.setSubscriptionId("sub-1");
        account.setTenantId("tenant-1");
        account.setClientId("client-1");
        account.setClientSecretEnc("secret");

        when(repository.findById(1L)).thenReturn(Optional.of(account));
        when(tokenManager.getToken("tenant-1", "client-1", "secret"))
                .thenReturn(AzureAccessToken.builder()
                        .accessToken("token")
                        .tokenType("Bearer")
                        .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                        .build());

        service.stopVirtualMachine(1L, "vm1", "rg", false);

        verify(apiClient).powerOffVirtualMachine("sub-1", "rg", "vm1", "token", false);
    }
}

