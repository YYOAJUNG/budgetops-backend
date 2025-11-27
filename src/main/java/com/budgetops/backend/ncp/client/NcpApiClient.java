package com.budgetops.backend.ncp.client;

import com.budgetops.backend.ncp.service.NcpSignatureGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
public class NcpApiClient {

    private static final String SERVER_API_BASE_URL = "https://ncloud.apigw.ntruss.com";
    private static final String BILLING_API_BASE_URL = "https://billingapi.apigw.ntruss.com/billing/v1";
    private static final String CLOUD_INSIGHT_API_BASE_URL = "https://cw.apigw.ntruss.com";

    private final NcpSignatureGenerator signatureGenerator;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public NcpApiClient(NcpSignatureGenerator signatureGenerator,
                        ObjectMapper objectMapper,
                        RestTemplateBuilder builder) {
        this.signatureGenerator = signatureGenerator;
        this.objectMapper = objectMapper;
        this.restTemplate = builder.build();
    }

    /**
     * Server API 호출
     */
    public JsonNode callServerApi(String path, Map<String, String> params, String accessKey, String secretKey) {
        String url = SERVER_API_BASE_URL + path;
        return get(url, params, accessKey, secretKey);
    }

    /**
     * Billing API 호출
     */
    public JsonNode callBillingApi(String path, Map<String, String> params, String accessKey, String secretKey) {
        String url = BILLING_API_BASE_URL + path;
        return get(url, params, accessKey, secretKey);
    }

    /**
     * Cloud Insight API 호출 (POST)
     * Cloud Insight의 QueryData API는 POST 메서드를 사용하며 Request Body로 조회 조건을 전달합니다.
     *
     * @param path API 경로 (예: /cw_fea/real/cw/api/data/query)
     * @param requestBody 요청 본문 (NcpMetricRequest 등)
     * @param accessKey NCP Access Key
     * @param secretKey NCP Secret Key
     * @return API 응답 (JsonNode)
     */
    public JsonNode callCloudInsightApi(String path, Object requestBody, String accessKey, String secretKey) {
        String url = CLOUD_INSIGHT_API_BASE_URL + path;
        return post(url, null, requestBody, accessKey, secretKey);
    }

    /**
     * GET 요청
     */
    private JsonNode get(String baseUrl, Map<String, String> params, String accessKey, String secretKey) {
        String timestamp = String.valueOf(System.currentTimeMillis());

        // URL with query parameters
        String fullUrl = withQueryParams(baseUrl, params);

        // URL path만 추출 (시그니처 생성용)
        String urlPath = fullUrl.substring(fullUrl.indexOf("/", 8)); // https:// 이후 첫 / 부터

        // 시그니처 생성
        String signature = signatureGenerator.generateSignature("GET", urlPath, timestamp, accessKey, secretKey);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ncp-apigw-timestamp", timestamp);
        headers.set("x-ncp-iam-access-key", accessKey);
        headers.set("x-ncp-apigw-signature-v2", signature);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            log.debug("NCP API Response received (length: {})", response.getBody() != null ? response.getBody().length() : 0);
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("NCP GET 호출 실패: url={}, status={}", fullUrl, e.getStatusCode());
            throw new IllegalStateException("NCP API 호출 실패: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("NCP GET 호출 중 알 수 없는 오류: url={}", fullUrl, e);
            throw new IllegalStateException("NCP API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * POST 요청
     */
    private JsonNode post(String baseUrl, Map<String, String> params, Object body, String accessKey, String secretKey) {
        String timestamp = String.valueOf(System.currentTimeMillis());

        // URL with query parameters
        String fullUrl = withQueryParams(baseUrl, params);

        // URL path만 추출 (시그니처 생성용)
        String urlPath = fullUrl.substring(fullUrl.indexOf("/", 8));

        // 시그니처 생성
        String signature = signatureGenerator.generateSignature("POST", urlPath, timestamp, accessKey, secretKey);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ncp-apigw-timestamp", timestamp);
        headers.set("x-ncp-iam-access-key", accessKey);
        headers.set("x-ncp-apigw-signature-v2", signature);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            log.debug("NCP API Response received (length: {})", response.getBody() != null ? response.getBody().length() : 0);
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("NCP POST 호출 실패: url={}, status={}", fullUrl, e.getStatusCode());
            throw new IllegalStateException("NCP API 호출 실패: " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("NCP POST 호출 중 알 수 없는 오류: url={}", fullUrl, e);
            throw new IllegalStateException("NCP API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * URL에 쿼리 파라미터 추가
     */
    private String withQueryParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        params.forEach(builder::queryParam);
        return builder.toUriString();
    }
}
