package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.ServiceAccountTestResponse;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GcpServiceAccountVerifier {

    public ServiceAccountTestResponse verifyServiceAccount(String serviceAccountId, String serviceAccountKeyJson) {
        ServiceAccountTestResponse res = new ServiceAccountTestResponse();
        Set<String> requiredRoles = new HashSet<>(Arrays.asList(
            "roles/viewer",
            "roles/monitoring.viewer",
            "roles/bigquery.dataViewer",
            "roles/bigquery.jobUser"
        ));

        if (serviceAccountId == null || serviceAccountId.isBlank() || serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            res.setOk(false);
            res.setMessage("service account id 또는 key json이 비어 있습니다.");
            res.setMissingRoles(Arrays.asList(
                "roles/viewer",
                "roles/monitoring.viewer",
                "roles/bigquery.dataViewer",
                "roles/bigquery.jobUser"
            ));
            return res;
        }
        try {
            // 서비스 계정 json으로 인증객체 생성
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(
                new ByteArrayInputStream(serviceAccountKeyJson.getBytes())
            );
            String projectId = credentials.getProjectId();

            // 1) BigQuery API 테스트 호출 (권한 체크 전 유효성/접근성 확인)
            BigQuery bigquery = BigQueryOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build().getService();
            for (Dataset ignored : bigquery.listDatasets().iterateAll()) { break; }
            // (실제 반환값 사용X, 단 호출만으로도 인증+API 확인)

            // 2) testIamPermissions로 필요한 최소 권한 보유 여부 확인 (REST 호출)
            Set<String> foundRoles = new HashSet<>();
            try {
                ServiceAccountCredentials scoped = (ServiceAccountCredentials) credentials.createScoped(
                        java.util.List.of("https://www.googleapis.com/auth/cloud-platform")
                );
                String accessToken = scoped.refreshAccessToken().getTokenValue();

                // 각 역할에 대응하는 핵심 권한
                java.util.Map<String, String> roleToPermission = new java.util.LinkedHashMap<>();
                roleToPermission.put("roles/viewer", "resourcemanager.projects.get");
                roleToPermission.put("roles/monitoring.viewer", "monitoring.timeSeries.list");
                roleToPermission.put("roles/bigquery.dataViewer", "bigquery.datasets.get");
                roleToPermission.put("roles/bigquery.jobUser", "bigquery.jobs.create");

                URL url = new URL("https://cloudresourcemanager.googleapis.com/v1/projects/" + projectId + ":testIamPermissions");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Authorization", "Bearer " + accessToken);
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setDoOutput(true);

                // 요청 본문 준비 (ObjectMapper로 안전 직렬화)
                java.util.List<String> permissions = new java.util.ArrayList<>(roleToPermission.values());
                ObjectMapper mapperForWrite = new ObjectMapper();
                com.fasterxml.jackson.databind.node.ObjectNode rootNode = mapperForWrite.createObjectNode();
                com.fasterxml.jackson.databind.node.ArrayNode permsArray = rootNode.putArray("permissions");
                for (String p : permissions) {
                    permsArray.add(p);
                }
                byte[] body = mapperForWrite.writeValueAsBytes(rootNode);
                con.getOutputStream().write(body);

                int code = con.getResponseCode();
                res.setHttpStatus(code);
                if (code >= 200 && code < 300) {
                    try (InputStream is = con.getInputStream()) {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(is);
                        JsonNode granted = root.path("permissions");
                        java.util.Set<String> grantedSet = new java.util.HashSet<>();
                        java.util.List<String> grantedList = new java.util.ArrayList<>();
                        if (granted.isArray()) {
                            for (JsonNode g : granted) {
                                String p = g.asText("");
                                grantedSet.add(p);
                                grantedList.add(p);
                            }
                        }
                        res.setGrantedPermissions(grantedList);
                        for (java.util.Map.Entry<String, String> e : roleToPermission.entrySet()) {
                            if (grantedSet.contains(e.getValue())) {
                                foundRoles.add(e.getKey());
                            }
                        }
                    }
                } else {
                    try (InputStream es = con.getErrorStream()) {
                        String snippet = readFirstN(es, 512);
                        res.setDebugBodySnippet(snippet);
                    }
                    res.setOk(false);
                    res.setMessage("권한 검사 실패: HTTP " + code);
                    res.setMissingRoles(new java.util.ArrayList<>(requiredRoles));
                    return res;
                }
            } catch (Exception e) {
                res.setOk(false);
                res.setMessage("권한 검사 실패: " + e.getMessage());
                res.setMissingRoles(new java.util.ArrayList<>(requiredRoles));
                return res;
            }

            Set<String> notGranted = requiredRoles.stream().filter(r -> !foundRoles.contains(r)).collect(Collectors.toSet());
            if (notGranted.isEmpty()) {
                res.setOk(true);
                res.setMessage("account/권한 테스트 성공");
                res.setMissingRoles(Arrays.asList());
            } else {
                res.setOk(false);
                res.setMessage("권한 누락: " + String.join(", ", notGranted));
                res.setMissingRoles(new java.util.ArrayList<>(notGranted));
            }
            return res;
        } catch (Exception ex) {
            res.setOk(false);
            res.setMessage("서비스 계정 인증 또는 테스트 실패: " + ex.getMessage());
            res.setMissingRoles(Arrays.asList(
                "roles/viewer",
                "roles/monitoring.viewer",
                "roles/bigquery.dataViewer",
                "roles/bigquery.jobUser"
            ));
            return res;
        }
    }

    private static String readFirstN(InputStream is, int maxBytes) {
        if (is == null) return null;
        try {
            byte[] buf = is.readNBytes(maxBytes);
            return new String(buf, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}


