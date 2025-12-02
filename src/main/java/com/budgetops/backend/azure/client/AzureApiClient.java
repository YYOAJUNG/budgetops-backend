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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
     * Virtual Machine 메트릭 조회
     * @param subscriptionId 구독 ID
     * @param resourceGroup 리소스 그룹 이름
     * @param vmName VM 이름
     * @param accessToken 액세스 토큰
     * @param hours 조회할 시간 범위 (기본값: 1시간)
     * @return 메트릭 데이터 (JSON)
     */
    public JsonNode getVirtualMachineMetrics(String subscriptionId, String resourceGroup, String vmName, String accessToken, Integer hours) {
        String resourceId = String.format("/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachines/%s",
                subscriptionId, resourceGroup, vmName);
        
        int hoursToQuery = hours != null && hours > 0 ? hours : 1;
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(hoursToQuery, ChronoUnit.HOURS);
        
        // Azure Monitor API 엔드포인트
        String baseUrl = ARM_BASE_URL + resourceId + "/providers/microsoft.insights/metrics";
        
        // 시간 범위 포맷: ISO 8601 형식 (예: 2023-12-01T10:00:00Z/2023-12-01T11:00:00Z)
        // Instant.toString()은 이미 ISO-8601 형식이지만, Azure API는 Z 대신 00:00 형식을 요구할 수 있음
        String timespan = formatTimespan(startTime, endTime);
        
        // 메트릭 이름들 - Azure API는 공백을 인코딩하지 않고 그대로 전달해야 함
        // Azure API 유효 메트릭: Percentage CPU, Network In, Network Out, Available Memory Bytes, Available Memory Percentage
        String metricNames = "Percentage CPU,Network In Total,Network Out Total,Available Memory Percentage,Available Memory Bytes";
        
        // interval 계산: 1시간이면 PT1H, 더 짧은 간격이 필요하면 조정
        String interval = hoursToQuery <= 1 ? "PT5M" : "PT1H"; // 1시간 이하면 5분 간격, 그 이상이면 1시간 간격
        
        // URL 수동 구성 - UriComponentsBuilder를 사용하여 자동 인코딩 (하지만 metricnames는 제외)
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        builder.queryParam("api-version", "2023-10-01");
        builder.queryParam("timespan", timespan);
        builder.queryParam("interval", interval);
        builder.queryParam("aggregation", "Average");
        builder.queryParam("metricNamespace", "microsoft.compute/virtualmachines");
        // metricnames는 공백을 인코딩하지 않고 그대로 추가
        String requestUrl = builder.toUriString() + "&metricnames=" + metricNames;
        
        log.debug("Azure metrics request URL: {}", requestUrl);
        
        return getRaw(requestUrl, accessToken);
    }
    
    /**
     * Azure API용 timespan 포맷팅
     */
    private String formatTimespan(Instant startTime, Instant endTime) {
        // Azure API는 ISO-8601 형식을 요구하지만, 초 단위까지 표시
        return startTime.toString() + "/" + endTime.toString();
    }
    
    /**
     * URL 컴포넌트 인코딩 (공백, 특수문자 등)
     */
    private String encodeUrlComponent(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")  // 공백은 %20으로
                    .replace("%2F", "/");  // 슬래시는 그대로 유지
        } catch (java.io.UnsupportedEncodingException e) {
            log.error("Failed to encode URL component: {}", value, e);
            return value;
        }
    }
    
    
    /**
     * Raw URL로 GET 요청 (수동으로 구성한 URL 사용)
     */
    private JsonNode getRaw(String url, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("Azure GET 호출 실패: url={}, status={}, body={}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Azure API 호출 실패: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Azure GET 호출 중 알 수 없는 오류: url={}", url, e);
            throw new IllegalStateException("Azure API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
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

