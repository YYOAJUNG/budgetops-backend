package com.budgetops.backend.aws.support;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class CryptoKeyProvider implements InitializingBean {

    @Value("${app.crypto.key:}")
    private String configuredKey;

    private static byte[] keyBytes;

    public static byte[] getKeyBytes() {
        if (keyBytes == null || keyBytes.length == 0) {
            throw new IllegalStateException("app.crypto.key 가 설정되지 않았습니다. (ENV: APP_CRED_KEY)");
        }
        return keyBytes;
    }

    @Override
    public void afterPropertiesSet() {
        // 디버깅: 환경 변수 직접 확인
        String envKey = System.getenv("APP_CRED_KEY");
        System.out.println("=== CryptoKeyProvider 초기화 ===");
        System.out.println("System.getenv('APP_CRED_KEY'): " + (envKey != null ? "설정됨 (길이: " + envKey.length() + ")" : "null"));
        System.out.println("@Value('app.crypto.key'): " + (configuredKey != null && !configuredKey.isBlank() ? "설정됨 (길이: " + configuredKey.length() + ")" : "null 또는 빈 문자열"));
        
        // 환경 변수에서 직접 읽기 시도 (application.yml이 제대로 로드되지 않은 경우 대비)
        if ((configuredKey == null || configuredKey.isBlank()) && envKey != null && !envKey.isBlank()) {
            System.out.println("경고: application.yml에서 키를 읽지 못했지만 환경 변수에서 발견했습니다. 환경 변수를 사용합니다.");
            configuredKey = envKey;
        }
        
        if (configuredKey == null || configuredKey.isBlank()) {
            // 키가 설정되지 않았을 때는 빈 배열로 설정하지 않고 null로 유지
            // getKeyBytes()에서 명확한 에러 메시지 제공
            keyBytes = null;
            System.err.println("에러: APP_CRED_KEY 환경 변수가 설정되지 않았습니다.");
            System.err.println("해결 방법:");
            System.err.println("1. 환경 변수 설정: export APP_CRED_KEY='your-key-here'");
            System.err.println("2. 또는 .env 파일에 APP_CRED_KEY=your-key-here 추가");
            System.err.println("3. 서버 재시작");
            return;
        }
        
        byte[] kb;
        
        // Base64로 인코딩된 키인지 확인 (일반적으로 base64 문자열은 = 로 끝나거나 특정 패턴을 가짐)
        // 또는 길이가 32바이트가 아닌 경우 base64 디코딩 시도
        try {
            // 먼저 base64 디코딩 시도
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            if (decoded.length == 32) {
                keyBytes = decoded;
                System.out.println("APP_CRED_KEY를 Base64 디코딩하여 사용합니다.");
                return;
            } else if (decoded.length == 16 || decoded.length == 24) {
                // AES-128 또는 AES-192 키인 경우 32바이트로 패딩
                byte[] out = new byte[32];
                System.arraycopy(decoded, 0, out, 0, decoded.length);
                keyBytes = out;
                System.out.println("APP_CRED_KEY를 Base64 디코딩하고 32바이트로 패딩했습니다.");
                return;
            }
        } catch (IllegalArgumentException e) {
            // Base64 디코딩 실패 - 일반 문자열로 처리
        }
        
        // 일반 문자열로 처리
        kb = configuredKey.getBytes();
        if (kb.length == 32) {
            keyBytes = kb;
            return;
        }
        
        // 32바이트로 패딩 또는 자르기
        byte[] out = new byte[32];
        if (kb.length < 32) {
            // 패딩: 부족한 부분을 0으로 채움
            System.arraycopy(kb, 0, out, 0, kb.length);
            System.out.println("경고: APP_CRED_KEY가 32바이트보다 짧습니다. 0으로 패딩합니다.");
        } else {
            // 자르기: 32바이트만 사용
            System.arraycopy(kb, 0, out, 0, 32);
            System.out.println("경고: APP_CRED_KEY가 32바이트보다 깁니다. 앞 32바이트만 사용합니다.");
        }
        keyBytes = out;
    }
}


