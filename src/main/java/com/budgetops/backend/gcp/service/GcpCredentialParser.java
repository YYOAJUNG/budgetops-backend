package com.budgetops.backend.gcp.service;

import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GcpCredentialParser {
    public static ServiceAccountCredentials parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("서비스 계정 JSON이 비어 있습니다.");
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return ServiceAccountCredentials.fromStream(bis);
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }
}