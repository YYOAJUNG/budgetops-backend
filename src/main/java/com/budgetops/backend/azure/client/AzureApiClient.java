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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class AzureApiClient {

    private static final String ARM_BASE_URL = "https://management.azure.com";
    private static final String SCOPE_MANAGEMENT = "https://management.azure.com/.default";
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
        params.put("api-version", "2023-09-01");
        return get(url, accessToken, params);
    }

    public JsonNode getVirtualMachineInstanceView(String subscriptionId, String resourceGroup, String vmName, String accessToken) {
        String url = ARM_BASE_URL + "/subscriptions/" + subscriptionId
                + "/resourceGroups/" + resourceGroup
                + "/providers/Microsoft.Compute/virtualMachines/" + vmName
                + "/instanceView";
        Map<String, String> params = new HashMap<>();
        params.put("api-version", "2023-09-01");
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

    /**
     * Azure VM 메트릭 조회
     * @param subscriptionId 구독 ID
     * @param resourceGroup 리소스 그룹
     * @param vmName VM 이름
     * @param accessToken 액세스 토큰
     * @param hours 조회할 시간 범위 (시간)
     * @return 메트릭 데이터 (JSON)
     */
    public JsonNode getVirtualMachineMetrics(String subscriptionId, String resourceGroup, String vmName, String accessToken, Integer hours) {
        String resourceId = String.format("/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachines/%s",
                subscriptionId, resourceGroup, vmName);
        
        // 시간 범위 계산 (기본값: 7일 = 168시간)
        int hoursToQuery = hours != null && hours > 0 ? hours : 168;
        java.time.Instant endTime = java.time.Instant.now();
        java.time.Instant startTime = endTime.minus(hoursToQuery, java.time.temporal.ChronoUnit.HOURS);
        
        // Azure Monitor API 엔드포인트
        String url = ARM_BASE_URL + resourceId + "/providers/microsoft.insights/metrics";
        
        Map<String, String> params = new HashMap<>();
        params.put("api-version", "2018-01-01");
        
        // timespan 형식: startTime/endTime (ISO 8601)
        String timespan = startTime.toString() + "/" + endTime.toString();
        params.put("timespan", timespan);
        
        // 메트릭 이름: CPU 사용률, 메모리 사용률
        params.put("metricnames", "Percentage CPU,Available Memory Bytes");
        
        // 집계 방식: Average
        params.put("aggregation", "Average");
        
        // 간격: 1시간
        params.put("interval", "PT1H");
        
        return get(url, accessToken, params);
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
            return objectMapper.readTree(response.getBody());
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
}

