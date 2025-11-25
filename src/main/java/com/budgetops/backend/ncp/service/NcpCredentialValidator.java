package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.client.NcpApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NcpCredentialValidator {

    private final NcpApiClient apiClient;

    /**
     * NCP 자격증명 유효성 검증
     * getServerInstanceList API를 호출하여 자격증명이 유효한지 확인합니다.
     *
     * @param accessKey NCP Access Key
     * @param secretKey NCP Secret Key
     * @param regionCode 리전 코드 (optional, null 가능)
     * @return 유효하면 true, 그렇지 않으면 false
     */
    public boolean isValid(String accessKey, String secretKey, String regionCode) {
        try {
            Map<String, String> params = new HashMap<>();
            if (regionCode != null && !regionCode.isBlank()) {
                params.put("regionCode", regionCode);
            }
            params.put("responseFormatType", "json");

            // getServerInstanceList API 호출
            apiClient.callServerApi("/vserver/v2/getServerInstanceList", params, accessKey, secretKey);

            log.info("NCP credential validation successful for accessKey: {}", accessKey);
            return true;
        } catch (Exception ex) {
            log.warn("NCP credential validation failed for accessKey: {}, error: {}", accessKey, ex.getMessage());
            return false;
        }
    }
}
