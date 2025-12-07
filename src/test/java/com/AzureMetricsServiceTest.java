package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.budgetops.backend.azure.dto.AzureVmMetricsResponse;
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

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureMetricsServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private AzureAccountRepository repository;

    @Mock
    private AzureApiClient apiClient;

    @Mock
    private AzureTokenManager tokenManager;

    @InjectMocks
    private AzureMetricsService service;

    @Test
    @DisplayName("메트릭 응답을 파싱한다")
    void getMetrics_success() throws Exception {
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
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build());

        JsonNode metricsNode = OBJECT_MAPPER.readTree("""
                {
                  "value": [
                    {
                      "name": {"value": "Percentage CPU"},
                      "unit": "Percent",
                      "timeseries": [
                        {
                          "data": [
                            {"timeStamp": "2025-12-01T10:00:00Z", "average": 12.5}
                          ]
                        }
                      ]
                    },
                    {
                      "name": {"value": "Network In Total"},
                      "unit": "Bytes",
                      "timeseries": [
                        {
                          "data": [
                            {"timeStamp": "2025-12-01T10:00:00Z", "total": 2048}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """);
        when(apiClient.getVirtualMachineMetrics(anyString(), anyString(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(metricsNode);

        AzureVmMetricsResponse response = service.getMetrics(1L, "rg", "vm1", 1);

        assertThat(response.getCpuUtilization()).hasSize(1);
        assertThat(response.getNetworkIn()).hasSize(1);
        assertThat(response.getNetworkOut()).isEmpty();
        assertThat(response.getCpuUtilization().get(0).getValue()).isEqualTo(12.5);
        assertThat(response.getNetworkIn().get(0).getValue()).isEqualTo(2048);
    }

    @Test
    @DisplayName("비활성 계정이면 예외가 발생한다")
    void getMetrics_inactiveAccount() {
        AzureAccount account = new AzureAccount();
        account.setId(1L);
        account.setActive(Boolean.FALSE);

        when(repository.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.getMetrics(1L, "rg", "vm1", 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("API 호출 실패 시 토큰 캐시를 무효화한다")
    void getMetrics_apiFailureInvalidatesToken() {
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
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build());

        when(apiClient.getVirtualMachineMetrics(anyString(), anyString(), any(), any(), any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.getMetrics(1L, "rg", "vm1", 1))
                .isInstanceOf(IllegalStateException.class);

        verify(tokenManager).invalidate("tenant-1", "client-1", "secret");
    }
}

