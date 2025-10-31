package com.budgetops.backend.aws.support;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        if (configuredKey == null || configuredKey.isBlank()) {
            keyBytes = new byte[0];
            return;
        }
        byte[] kb = configuredKey.getBytes();
        if (kb.length == 32) {
            keyBytes = kb;
            return;
        }
        byte[] out = new byte[32];
        System.arraycopy(kb, 0, out, 0, Math.min(kb.length, 32));
        keyBytes = out;
    }
}




