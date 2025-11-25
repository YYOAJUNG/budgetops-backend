package com.budgetops.backend.ncp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class NcpSignatureGenerator {

    /**
     * NCP API 요청을 위한 시그니처를 생성합니다.
     *
     * @param method HTTP 메서드 (GET, POST 등)
     * @param url API 경로 (쿼리 파라미터 포함, 예: /vserver/v2/getServerInstanceList?regionCode=KR)
     * @param timestamp Unix timestamp (밀리초)
     * @param accessKey NCP Access Key
     * @param secretKey NCP Secret Key
     * @return Base64로 인코딩된 시그니처
     */
    public String generateSignature(String method, String url, String timestamp, String accessKey, String secretKey) {
        try {
            String space = " ";
            String newLine = "\n";

            // 시그니처 메시지 생성
            String message = new StringBuilder()
                    .append(method)
                    .append(space)
                    .append(url)
                    .append(newLine)
                    .append(timestamp)
                    .append(newLine)
                    .append(accessKey)
                    .toString();

            // HmacSHA256으로 서명
            SecretKeySpec signingKey = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);

            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);

        } catch (Exception e) {
            log.error("Failed to generate NCP signature", e);
            throw new IllegalStateException("NCP 시그니처 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
