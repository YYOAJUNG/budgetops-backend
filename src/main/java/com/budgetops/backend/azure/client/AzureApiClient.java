package com.budgetops.backend.azure.client;

import com.budgetops.backend.azure.dto.AzureAccessToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class AzureApiClient {

    private static final String ARM_BASE_URL = "https://management.azure.com";
    private static final String SCOPE_MANAGEMENT = "https://management.azure.com/.default";
    private static final String COMPUTE_API_VERSION = "2023-09-01";
    private static final String METRICS_API_VERSION = "2023-10-01";
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AzureApiClient(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder.build();
        this.objectMapper = objectMapper;
    }

    public AzureAccessToken fetchToken(String tenantId, String clientId, String clientSecret) {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("scope", SCOPE_MANAGEMENT);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(formData, headers),
                    String.class
            );

            JsonNode body = objectMapper.readTree(response.getBody());
            String accessToken = body.path("access_token").asText(null);
            String tokenType = body.path("token_type").asText("Bearer");
            int expiresIn = body.path("expires_in").asInt(3600);

            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("Azure 액세스 토큰을 가져올 수 없습니다.");
            }

            return AzureAccessToken.builder()
                    .accessToken(accessToken)
                    .tokenType(tokenType)
                    .expiresAt(OffsetDateTime.now().plusSeconds(expiresIn))
                    .build();

        } catch (HttpStatusCodeException e) {
            log.error("Azure 토큰 발급 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Azure 토큰 발급에 실패했습니다: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Azure 토큰 발급 중 알 수 없는 오류", e);
            throw new IllegalStateException("Azure 토큰 발급 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    public JsonNode getSubscription(String subscriptionId, String accessToken) {
        String url = ARM_BASE_URL + "/subscriptions/" + subscriptionId;
        Map<String, String> params = new HashMap<>();
        params.put("api-version", "2020-01-01");
        return get(url, accessToken, params);
    }

    public JsonNode listVirtualMachines(String subscriptionId, String accessToken) {
        String url = ARM_BASE_URL + "/subscriptions/" + subscriptionId + "/providers/Microsoft.Compute/virtualMachines";
        Map<String, String> params = new HashMap<>();
        params.put("api-version", COMPUTE_API_VERSION);
        return get(url, accessToken, params);
    }

    public JsonNode getVirtualMachineInstanceView(String subscriptionId, String resourceGroup, String vmName, String accessToken) {
        String url = ARM_BASE_URL + "/subscriptions/" + subscriptionId
                + "/resourceGroups/" + resourceGroup
                + "/providers/Microsoft.Compute/virtualMachines/" + vmName
                + "/instanceView";
        Map<String, String> params = new HashMap<>();
        params.put("api-version", COMPUTE_API_VERSION);
        return get(url, accessToken, params);
    }

    public JsonNode getNetworkInterface(String subscriptionId, String resourceGroup, String nicName, String accessToken) {
        String url = ARM_BASE_URL + "/subscriptions/" + subscriptionId
                + "/resourceGroups/" + resourceGroup
                + "/providers/Microsoft.Network/networkInterfaces/" + nicName;
        Map<String, String> params = new HashMap<>();
        params.put("api-version", "2023-04-01");
        return get(url, accessToken, params);
    }

    public JsonNode getPublicIpAddress(String subscriptionId, String resourceGroup, String publicIpName, String accessToken) {
        String url = ARM_BASE_URL + "/subscriptions/" + subscriptionId
                + "/resourceGroups/" + resourceGroup
                + "/providers/Microsoft.Network/publicIPAddresses/" + publicIpName;
        Map<String, String> params = new HashMap<>();
        params.put("api-version", "2023-04-01");
        return get(url, accessToken, params);
    }

    public JsonNode queryCosts(String subscriptionId, String accessToken, String fromDate, String toDate, String granularity) {
        String url = ARM_BASE_URL + "/subscriptions/" + subscriptionId + "/providers/Microsoft.CostManagement/query";
        Map<String, Object> body = new HashMap<>();
        body.put("type", "ActualCost");

        Map<String, Object> timePeriod = new HashMap<>();
        timePeriod.put("from", fromDate);
        timePeriod.put("to", toDate);

        Map<String, Object> dataset = new HashMap<>();
        dataset.put("granularity", granularity);

        Map<String, Object> aggregation = new HashMap<>();
        Map<String, Object> totalCost = new HashMap<>();
        totalCost.put("name", "Cost");
        totalCost.put("function", "Sum");
        aggregation.put("totalCost", totalCost);
        dataset.put("aggregation", aggregation);

        body.put("timeframe", "Custom");
        body.put("timePeriod", timePeriod);
        body.put("dataset", dataset);

        Map<String, String> params = new HashMap<>();
        params.put("api-version", "2023-08-01");

        return post(url, accessToken, params, body);
    }

    public JsonNode getVirtualMachineMetrics(
            String resourceId,
            String accessToken,
            Instant startTime,
            Instant endTime,
            Duration interval,
            String metricNames,
            String aggregations
    ) {
        String normalizedId = resourceId.startsWith("/") ? resourceId : "/" + resourceId;
        String baseUrl = ARM_BASE_URL + normalizedId + "/providers/microsoft.insights/metrics";

        // Azure Metrics API가 metricnames 값을 다시 디코딩하면서 이중 인코딩된 값
        // (예: Percentage%2520CPU)을 그대로 비교해 400을 반환하는 문제가 있어,
        // 이 엔드포인트에 한해서는 쿼리 파라미터를 직접 구성해 추가 인코딩을 피한다.
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?api-version=").append(METRICS_API_VERSION);
        urlBuilder.append("&timespan=").append(formatTimespan(startTime, endTime));
        urlBuilder.append("&aggregation=").append(aggregations);
        if (interval != null && !interval.isZero() && !interval.isNegative()) {
            urlBuilder.append("&interval=").append(formatInterval(interval));
        }
        urlBuilder.append("&metricNamespace=microsoft.compute/virtualmachines");
        if (metricNames != null && !metricNames.isBlank()) {
            // 공백/콤마가 포함된 메트릭 이름을 그대로 전달하고 RestTemplate 한 번만 인코딩하도록 맡긴다.
            // 이렇게 하면 Azure 측에서 디코딩 후 "Percentage CPU", "Network In Total" 등
            // 유효한 메트릭 이름과 정상적으로 매칭된다.
            urlBuilder.append("&metricnames=").append(metricNames);
        }

        String requestUrl = urlBuilder.toString();
        return getRaw(requestUrl, accessToken);
    }

    public void deleteVirtualMachine(String subscriptionId, String resourceGroup, String vmName, String accessToken) {
        String baseUrl = ARM_BASE_URL + "/subscriptions/" + subscriptionId
                + "/resourceGroups/" + resourceGroup
                + "/providers/Microsoft.Compute/virtualMachines/" + vmName;

        Map<String, String> params = new HashMap<>();
        params.put("api-version", COMPUTE_API_VERSION);

        delete(baseUrl, accessToken, params);
    }

    public void startVirtualMachine(String subscriptionId, String resourceGroup, String vmName, String accessToken) {
        String url = buildVirtualMachineActionUrl(subscriptionId, resourceGroup, vmName, "start");
        Map<String, String> params = new HashMap<>();
        params.put("api-version", COMPUTE_API_VERSION);
        post(url, accessToken, params, null);
    }

    public void powerOffVirtualMachine(String subscriptionId, String resourceGroup, String vmName, String accessToken, boolean skipShutdown) {
        String url = buildVirtualMachineActionUrl(subscriptionId, resourceGroup, vmName, "powerOff");
        Map<String, String> params = new HashMap<>();
        params.put("api-version", COMPUTE_API_VERSION);
        if (skipShutdown) {
            params.put("skipShutdown", "true");
        }
        post(url, accessToken, params, null);
    }

    public void deallocateVirtualMachine(String subscriptionId, String resourceGroup, String vmName, String accessToken) {
        String url = buildVirtualMachineActionUrl(subscriptionId, resourceGroup, vmName, "deallocate");
        Map<String, String> params = new HashMap<>();
        params.put("api-version", COMPUTE_API_VERSION);
        post(url, accessToken, params, null);
    }

    private JsonNode get(String url, String accessToken, Map<String, String> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String requestUrl = withQueryParams(url, params);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("Azure GET 호출 실패: url={}, status={}, body={}", requestUrl, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Azure API 호출 실패: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Azure GET 호출 중 알 수 없는 오류: url={}", requestUrl, e);
            throw new IllegalStateException("Azure API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private void delete(String baseUrl, String accessToken, Map<String, String> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String requestUrl = withQueryParams(baseUrl, params);
        try {
            restTemplate.exchange(
                    requestUrl,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class
            );
        } catch (HttpStatusCodeException e) {
            log.error("Azure DELETE 호출 실패: url={}, status={}, body={}", requestUrl, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Azure API 호출 실패: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Azure DELETE 호출 중 알 수 없는 오류: url={}", requestUrl, e);
            throw new IllegalStateException("Azure API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 쿼리 파라미터가 이미 인코딩된 전체 URL을 그대로 사용해 GET 호출을 수행한다.
     * Azure Metrics API와 같이 인코딩 방식에 민감한 엔드포인트에만 사용한다.
     */
    private JsonNode getRaw(String requestUrl, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("Azure GET 호출 실패: url={}, status={}, body={}", requestUrl, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Azure API 호출 실패: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Azure GET 호출 중 알 수 없는 오류: url={}", requestUrl, e);
            throw new IllegalStateException("Azure API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private JsonNode post(String url, String accessToken, Map<String, String> params, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestUrl = withQueryParams(url, params);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (HttpStatusCodeException e) {
            log.error("Azure POST 호출 실패: url={}, status={}, body={}", requestUrl, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Azure API 호출 실패: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Azure POST 호출 중 알 수 없는 오류: url={}", requestUrl, e);
            throw new IllegalStateException("Azure API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private String withQueryParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        params.forEach(builder::queryParam);
        return builder.toUriString();
    }

    private String buildVirtualMachineActionUrl(String subscriptionId, String resourceGroup, String vmName, String action) {
        return ARM_BASE_URL + "/subscriptions/" + subscriptionId
                + "/resourceGroups/" + resourceGroup
                + "/providers/Microsoft.Compute/virtualMachines/" + vmName
                + "/" + action;
    }

    private String formatTimespan(Instant start, Instant end) {
        return ISO_INSTANT.format(start) + "/" + ISO_INSTANT.format(end);
    }

    private String formatInterval(Duration duration) {
        long minutes = Math.max(1, duration.toMinutes());
        return "PT" + minutes + "M";
    }
}

