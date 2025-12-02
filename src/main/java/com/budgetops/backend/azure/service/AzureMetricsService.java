package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.client.AzureApiClient;
import com.budgetops.backend.azure.dto.AzureAccessToken;
import com.budgetops.backend.azure.dto.AzureVmMetricsResponse;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureMetricsService {

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(5);
    private static final int MAX_RANGE_HOURS = 24;
    private static final String METRIC_CPU = "Percentage CPU";
    private static final String METRIC_NETWORK_IN = "Network In Total";
    private static final String METRIC_NETWORK_OUT = "Network Out Total";

    private final AzureAccountRepository accountRepository;
    private final AzureApiClient apiClient;
    private final AzureTokenManager tokenManager;

    @Transactional(readOnly = true)
    public AzureVmMetricsResponse getMetrics(Long accountId, String resourceGroup, String vmName, Integer hours) {
        AzureAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Azure 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 Azure 계정입니다.");
        }

        validateRequired(resourceGroup, "resourceGroup");
        validateRequired(vmName, "vmName");

        int hoursToQuery = normalizeHours(hours);
        Instant endTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant startTime = endTime.minus(hoursToQuery, ChronoUnit.HOURS);

        AzureAccessToken token = tokenManager.getToken(
                account.getTenantId(),
                account.getClientId(),
                account.getClientSecretEnc()
        );

        JsonNode metricsNode;
        try {
            metricsNode = apiClient.getVirtualMachineMetrics(
                    buildResourceId(account.getSubscriptionId(), resourceGroup, vmName),
                    token.getAccessToken(),
                    startTime,
                    endTime,
                    DEFAULT_INTERVAL,
                    String.join(",", List.of(METRIC_CPU, METRIC_NETWORK_IN, METRIC_NETWORK_OUT)),
                    "Average,Total"
            );
        } catch (Exception e) {
            tokenManager.invalidate(account.getTenantId(), account.getClientId(), account.getClientSecretEnc());
            throw e;
        }

        return AzureVmMetricsResponse.builder()
                .vmName(vmName)
                .resourceGroup(resourceGroup)
                .cpuUtilization(parseMetricSeries(metricsNode, METRIC_CPU))
                .networkIn(parseMetricSeries(metricsNode, METRIC_NETWORK_IN))
                .networkOut(parseMetricSeries(metricsNode, METRIC_NETWORK_OUT))
                .memoryUtilization(Collections.emptyList())
                .build();
    }

    private String buildResourceId(String subscriptionId, String resourceGroup, String vmName) {
        return "/subscriptions/" + subscriptionId
                + "/resourceGroups/" + resourceGroup
                + "/providers/Microsoft.Compute/virtualMachines/" + vmName;
    }

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 파라미터는 필수입니다.");
        }
    }

    private int normalizeHours(Integer hours) {
        if (hours == null || hours <= 0) {
            return 1;
        }
        return Math.min(hours, MAX_RANGE_HOURS);
    }

    private List<AzureVmMetricsResponse.MetricDataPoint> parseMetricSeries(JsonNode root, String metricName) {
        if (root == null || root.isMissingNode()) {
            return Collections.emptyList();
        }

        List<AzureVmMetricsResponse.MetricDataPoint> result = new ArrayList<>();
        JsonNode valueArray = root.path("value");
        if (!valueArray.isArray()) {
            return result;
        }

        for (JsonNode metricNode : valueArray) {
            String current = metricNode.path("name").path("value").asText("");
            if (!metricName.equalsIgnoreCase(current)) {
                continue;
            }

            String unit = metricNode.path("unit").asText("");
            JsonNode timeseriesArray = metricNode.path("timeseries");
            if (!timeseriesArray.isArray()) {
                continue;
            }

            for (JsonNode series : timeseriesArray) {
                JsonNode dataArray = series.path("data");
                if (!dataArray.isArray()) {
                    continue;
                }

                for (JsonNode dataPoint : dataArray) {
                    Optional<Double> value = extractValue(dataPoint);
                    if (value.isEmpty()) {
                        continue;
                    }
                    String timestamp = dataPoint.path("timeStamp").asText(dataPoint.path("timestamp").asText(""));
                    result.add(AzureVmMetricsResponse.MetricDataPoint.builder()
                            .timestamp(timestamp)
                            .value(value.get())
                            .unit(unit)
                            .build());
                }
            }
        }

        result.sort(Comparator.comparing(AzureVmMetricsResponse.MetricDataPoint::getTimestamp));
        return result;
    }

    private Optional<Double> extractValue(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return Optional.empty();
        }
        if (node.hasNonNull("average")) {
            return Optional.of(node.get("average").asDouble());
        }
        if (node.hasNonNull("total")) {
            return Optional.of(node.get("total").asDouble());
        }
        if (node.hasNonNull("maximum")) {
            return Optional.of(node.get("maximum").asDouble());
        }
        if (node.hasNonNull("minimum")) {
            return Optional.of(node.get("minimum").asDouble());
        }
        return Optional.empty();
    }
}

