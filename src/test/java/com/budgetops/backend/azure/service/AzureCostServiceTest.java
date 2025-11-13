package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.billing.entity.Workspace;
import com.budgetops.backend.domain.user.entity.Member;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureCostServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long MEMBER_ID = 1L;

    @Mock
    private AzureAccountRepository repository;

    @Mock
    private AzureApiClient apiClient;

    @Mock
    private AzureTokenManager tokenManager;

    @Mock
    private AzureCostRateLimiter rateLimiter;

    @InjectMocks
    private AzureCostService service;

    private AzureAccount account;
    private AzureAccessToken token;

    @BeforeEach
    void setUp() {
        Member member = new Member();
        member.setId(MEMBER_ID);
        member.setName("Tester");
        member.setEmail("tester@example.com");

        Workspace workspace = new Workspace();
        workspace.setId(44L);
        workspace.setOwner(member);

        account = new AzureAccount();
        account.setId(1L);
        account.setActive(Boolean.TRUE);
        account.setSubscriptionId("sub-1");
        account.setTenantId("tenant-1");
        account.setClientId("client-1");
        account.setClientSecretEnc("secret-1");
        account.setWorkspace(workspace);

        token = AzureAccessToken.builder()
                .accessToken("token")
                .tokenType("Bearer")
                .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                .build();
    }

    @Test
    @DisplayName("일별 비용 조회는 Azure Cost API 응답을 매핑한다")
    void getDailyCosts_success() throws Exception {
        when(repository.findByIdAndWorkspaceOwnerId(1L, MEMBER_ID)).thenReturn(Optional.of(account));
        when(tokenManager.getToken("tenant-1", "client-1", "secret-1")).thenReturn(token);

        JsonNode response = OBJECT_MAPPER.readTree("""
                {
                  "properties": {
                    "columns": [
                      {"name": "UsageDate"},
                      {"name": "Cost"},
                      {"name": "Currency"}
                    ],
                    "rows": [
                      ["2025-01-01", 12.34, "USD"],
                      ["2025-01-02", 3.21, "USD"]
                    ]
                  }
                }
                """);

        when(apiClient.queryCosts(eq("sub-1"), anyString(), eq("2025-01-01"), eq("2025-01-31"), eq("Daily")))
                .thenReturn(response);

        List<AzureCostService.DailyCost> costs = service.getCosts(1L, MEMBER_ID, "2025-01-01", "2025-01-31");

        assertThat(costs).hasSize(2);
        assertThat(costs.get(0).getAmount()).isEqualTo(12.34);
        verify(rateLimiter).awaitAllowance("sub-1");
    }

    @Test
    @DisplayName("월별 비용 조회는 총 금액과 통화를 반환한다")
    void getMonthlyCost_success() throws Exception {
        when(repository.findByIdAndWorkspaceOwnerId(1L, MEMBER_ID)).thenReturn(Optional.of(account));
        when(tokenManager.getToken("tenant-1", "client-1", "secret-1")).thenReturn(token);

        JsonNode response = OBJECT_MAPPER.readTree("""
                {
                  "properties": {
                    "columns": [
                      {"name": "Cost"},
                      {"name": "Currency"}
                    ],
                    "rows": [
                      [55.0, "USD"]
                    ]
                  }
                }
                """);

        when(apiClient.queryCosts(eq("sub-1"), anyString(), anyString(), anyString(), eq("None")))
                .thenReturn(response);

        AzureCostService.MonthlyCost cost = service.getMonthlyCost(1L, MEMBER_ID, 2025, 1);

        assertThat(cost.getAmount()).isEqualTo(55.0);
        assertThat(cost.getCurrency()).isEqualTo("USD");
        verify(rateLimiter).awaitAllowance("sub-1");
    }

    @Test
    @DisplayName("여러 계정 비용 조회 중 오류가 발생해도 나머지는 계속 처리된다")
    void getAllAccountsCosts_handlesFailures() throws Exception {
        AzureAccount successAccount = account;

        AzureAccount failAccount = new AzureAccount();
        failAccount.setId(2L);
        failAccount.setActive(Boolean.TRUE);
        failAccount.setSubscriptionId("sub-2");
        failAccount.setTenantId("tenant-2");
        failAccount.setClientId("client-2");
        failAccount.setClientSecretEnc("secret-2");
        failAccount.setName("Fail");
        failAccount.setWorkspace(account.getWorkspace());

        successAccount.setName("Success");

        when(repository.findByWorkspaceOwnerIdAndActiveTrue(MEMBER_ID)).thenReturn(List.of(successAccount, failAccount));
        when(tokenManager.getToken("tenant-1", "client-1", "secret-1")).thenReturn(token);
        when(tokenManager.getToken("tenant-2", "client-2", "secret-2"))
                .thenReturn(token);

        JsonNode response = OBJECT_MAPPER.readTree("""
                {
                  "properties": {
                    "columns": [
                      {"name": "Cost"},
                      {"name": "Currency"}
                    ],
                    "rows": [
                      [10.0, "USD"]
                    ]
                  }
                }
                """);

        when(apiClient.queryCosts(eq("sub-1"), anyString(), anyString(), anyString(), eq("None")))
                .thenReturn(response);
        when(apiClient.queryCosts(eq("sub-2"), anyString(), anyString(), anyString(), eq("None")))
                .thenThrow(new IllegalStateException("boom"));

        List<AzureCostService.AccountCost> costs = service.getAllAccountsCosts(MEMBER_ID, "2025-01-01", "2025-01-31");

        assertThat(costs).hasSize(2);
        assertThat(costs).anyMatch(c -> c.getAccountId().equals(1L) && c.getAmount() == 10.0);
        assertThat(costs).anyMatch(c -> c.getAccountId().equals(2L) && c.getAmount() == 0.0);

        verify(tokenManager).invalidate("tenant-2", "client-2", "secret-2");
    }
}
