package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.client.NcpApiClient;
import com.budgetops.backend.ncp.dto.NcpServerInstanceResponse;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class NcpServerService {

    private final NcpAccountRepository accountRepository;
    private final NcpApiClient apiClient;

    /**
     * 특정 NCP 계정의 서버 인스턴스 목록 조회
     *
     * @param accountId NCP 계정 ID
     * @param regionCode 조회할 리전 (null이면 계정의 기본 리전)
     * @return 서버 인스턴스 목록
     */
    public List<NcpServerInstanceResponse> listInstances(Long accountId, String regionCode) {
        NcpAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "NCP 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }

        String targetRegion = regionCode != null ? regionCode : account.getRegionCode();

        log.info("Fetching NCP server instances for account {} in region {}", accountId, targetRegion);

        try {
            Map<String, String> params = new HashMap<>();
            if (targetRegion != null && !targetRegion.isBlank()) {
                params.put("regionCode", targetRegion);
            }
            params.put("responseFormatType", "json");

            JsonNode response = apiClient.callServerApi(
                    "/vserver/v2/getServerInstanceList",
                    params,
                    account.getAccessKey(),
                    account.getSecretKeyEnc()
            );

            log.info("NCP API Response: {}", response.toString());
            List<NcpServerInstanceResponse> instances = parseServerInstanceList(response);
            log.info("Found {} server instances in region {} for account {}", instances.size(), targetRegion, accountId);
            return instances;

        } catch (Exception e) {
            log.error("Failed to fetch server instances for account {} in region {}: {}", accountId, targetRegion, e.getMessage(), e);
            throw new RuntimeException("서버 인스턴스 조회 실패: " + e.getMessage());
        }
    }

    /**
     * 서버 인스턴스 시작
     */
    public List<NcpServerInstanceResponse> startInstances(Long accountId, List<String> serverInstanceNos, String regionCode) {
        NcpAccount account = getActiveAccount(accountId);

        log.info("Starting NCP server instances {} for account {}", serverInstanceNos, accountId);

        try {
            Map<String, String> params = buildServerActionParams(serverInstanceNos, regionCode, account);

            JsonNode response = apiClient.callServerApi(
                    "/vserver/v2/startServerInstances",
                    params,
                    account.getAccessKey(),
                    account.getSecretKeyEnc()
            );

            return parseServerInstanceList(response);

        } catch (Exception e) {
            log.error("Failed to start server instances for account {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("서버 시작 실패: " + e.getMessage());
        }
    }

    /**
     * 서버 인스턴스 정지
     */
    public List<NcpServerInstanceResponse> stopInstances(Long accountId, List<String> serverInstanceNos, String regionCode) {
        NcpAccount account = getActiveAccount(accountId);

        log.info("Stopping NCP server instances {} for account {}", serverInstanceNos, accountId);

        try {
            Map<String, String> params = buildServerActionParams(serverInstanceNos, regionCode, account);

            JsonNode response = apiClient.callServerApi(
                    "/vserver/v2/stopServerInstances",
                    params,
                    account.getAccessKey(),
                    account.getSecretKeyEnc()
            );

            return parseServerInstanceList(response);

        } catch (Exception e) {
            log.error("Failed to stop server instances for account {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("서버 정지 실패: " + e.getMessage());
        }
    }

    /**
     * 활성화된 계정 조회
     */
    private NcpAccount getActiveAccount(Long accountId) {
        NcpAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "NCP 계정을 찾을 수 없습니다."));

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException("비활성화된 계정입니다.");
        }

        return account;
    }

    /**
     * 서버 액션용 파라미터 빌드
     */
    private Map<String, String> buildServerActionParams(List<String> serverInstanceNos, String regionCode, NcpAccount account) {
        Map<String, String> params = new HashMap<>();

        String targetRegion = regionCode != null ? regionCode : account.getRegionCode();
        if (targetRegion != null && !targetRegion.isBlank()) {
            params.put("regionCode", targetRegion);
        }

        // serverInstanceNoList.1=xxx&serverInstanceNoList.2=yyy 형식
        for (int i = 0; i < serverInstanceNos.size(); i++) {
            params.put("serverInstanceNoList." + (i + 1), serverInstanceNos.get(i));
        }

        params.put("responseFormatType", "json");
        return params;
    }

    /**
     * JSON 응답을 파싱하여 서버 인스턴스 목록 반환
     */
    private List<NcpServerInstanceResponse> parseServerInstanceList(JsonNode response) {
        List<NcpServerInstanceResponse> instances = new ArrayList<>();

        // 응답 형식: getServerInstanceListResponse, startServerInstancesResponse, stopServerInstancesResponse 등
        JsonNode responseNode = response.fields().hasNext() ? response.fields().next().getValue() : response;

        if (responseNode.has("serverInstanceList")) {
            JsonNode serverList = responseNode.get("serverInstanceList");
            if (serverList.isArray()) {
                for (JsonNode server : serverList) {
                    instances.add(parseServerInstance(server));
                }
            }
        }

        return instances;
    }

    /**
     * 단일 서버 인스턴스 파싱
     */
    private NcpServerInstanceResponse parseServerInstance(JsonNode server) {
        return NcpServerInstanceResponse.builder()
                .serverInstanceNo(server.path("serverInstanceNo").asText(null))
                .serverName(server.path("serverName").asText(null))
                .serverDescription(server.path("serverDescription").asText(null))
                .cpuCount(server.path("cpuCount").asInt(0))
                .memorySize(server.path("memorySize").asLong(0L))
                .platformType(server.path("platformType").path("codeName").asText(null))
                .publicIp(server.path("publicIp").asText(null))
                .serverInstanceStatus(server.path("serverInstanceStatus").path("code").asText(null))
                .serverInstanceStatusName(server.path("serverInstanceStatusName").asText(null))
                .createDate(server.path("createDate").asText(null))
                .uptime(server.path("uptime").asText(null))
                .serverImageProductCode(server.path("serverImageProductCode").asText(null))
                .serverProductCode(server.path("serverProductCode").asText(null))
                .zoneCode(server.path("zoneCode").asText(null))
                .regionCode(server.path("regionCode").asText(null))
                .vpcNo(server.path("vpcNo").asText(null))
                .subnetNo(server.path("subnetNo").asText(null))
                .build();
    }
}
