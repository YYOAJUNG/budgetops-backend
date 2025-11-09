package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.dto.GcpResourceResponse;
import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.entity.GcpResource;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.repository.GcpResourceRepository;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.asset.v1.AssetServiceClient;
import com.google.cloud.asset.v1.AssetServiceSettings;
import com.google.cloud.asset.v1.ResourceSearchResult;
import com.google.cloud.asset.v1.SearchAllResourcesRequest;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GcpResourceService {
    private final GcpResourceRepository resourceRepository;
    private final GcpAccountRepository accountRepository;

    @Transactional
    public GcpResourceListResponse listResources(Long accountId) {
        GcpAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("GCP 계정을 찾을 수 없습니다: " + accountId));

        String serviceAccountKeyJson = account.getEncryptedServiceAccountKey();
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            throw new IllegalStateException("서비스 계정 키가 설정되지 않았습니다.");
        }

        ServiceAccountCredentials credentials = GcpCredentialParser.parse(serviceAccountKeyJson);
        String projectId = account.getProjectId();
        Instant now = Instant.now();

        List<GcpResource> resources = new ArrayList<>();

        try {
            AssetServiceSettings settings = AssetServiceSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            try (AssetServiceClient client = AssetServiceClient.create(settings)) {
                String parent = "projects/" + projectId;
                SearchAllResourcesRequest request = SearchAllResourcesRequest.newBuilder()
                        .setScope(parent)
                        .build();

                for (ResourceSearchResult result : client.searchAllResources(request).iterateAll()) {
                    GcpResource resource = convertResourceSearchResultToResource(result, account, now);
                    resources.add(resource);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Cloud Asset API 호출 실패: " + e.getMessage(), e);
        }

        // DB에 저장 또는 업데이트
        List<GcpResource> savedResources = new ArrayList<>();
        for (GcpResource resource : resources) {
            Optional<GcpResource> existing = resourceRepository.findByResourceIdAndGcpAccountId(
                    resource.getResourceId(), accountId);
            if (existing.isPresent()) {
                GcpResource existingResource = existing.get();
                existingResource.setResourceType(resource.getResourceType());
                existingResource.setResourceName(resource.getResourceName());
                existingResource.setRegion(resource.getRegion());
                existingResource.setStatus(resource.getStatus());
                existingResource.setDescription(resource.getDescription());
                existingResource.setLastUpdated(now);
                savedResources.add(resourceRepository.save(existingResource));
            } else {
                savedResources.add(resourceRepository.save(resource));
            }
        }

        // DTO로 변환
        List<GcpResourceResponse> responseList = savedResources.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        GcpResourceListResponse response = new GcpResourceListResponse();
        response.setAccountId(accountId);
        response.setProjectId(account.getProjectId());
        response.setResources(responseList);
        return response;
    }

    private GcpResource convertResourceSearchResultToResource(ResourceSearchResult result, GcpAccount account, Instant now) {
        String resourceId = result.getName();
        String resourceType = result.getAssetType();
        String resourceName = extractResourceName(result);
        String region = extractRegion(result);
        String status = extractStatus(result);
        String description = null;

        return GcpResource.builder()
                .resourceId(resourceId)
                .resourceType(resourceType)
                .resourceName(resourceName)
                .region(region)
                .status(status)
                .description(description)
                .monthlyCost(null) // 나중에 별도 API로 채워질 예정
                .lastUpdated(now)
                .gcpAccount(account)
                .build();
    }

    private String extractResourceName(ResourceSearchResult result) {
        // ResourceSearchResult의 name에서 리소스 이름 추출
        // 예: "//compute.googleapis.com/projects/my-project/zones/us-central1-a/instances/my-instance"
        String name = result.getName();
        if (name == null || name.isEmpty()) {
            // displayName이 있으면 사용
            String displayName = result.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
            return "Unknown";
        }
        String[] parts = name.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }
        return name;
    }

    private String extractRegion(ResourceSearchResult result) {
        // ResourceSearchResult의 location 정보 추출
        try {
            // 먼저 직접 location 필드 확인
            String location = result.getLocation();
            if (location != null && !location.isEmpty()) {
                // zone에서 region 추출 (예: "us-central1-a" -> "us-central1")
                if (location.contains("-")) {
                    String[] parts = location.split("-");
                    if (parts.length >= 2) {
                        return parts[0] + "-" + parts[1];
                    }
                }
                return location;
            }
            
            // additionalAttributes에서 location 정보 추출
            Struct additionalAttributes = result.getAdditionalAttributes();
            if (additionalAttributes != null) {
                Map<String, Value> fields = additionalAttributes.getFieldsMap();
                if (fields.containsKey("location")) {
                    Value locationValue = fields.get("location");
                    if (locationValue.hasStringValue()) {
                        return locationValue.getStringValue();
                    }
                }
                if (fields.containsKey("zone")) {
                    Value zoneValue = fields.get("zone");
                    if (zoneValue.hasStringValue()) {
                        String zone = zoneValue.getStringValue();
                        // zone에서 region 추출 (예: "us-central1-a" -> "us-central1")
                        if (zone.contains("-")) {
                            String[] parts = zone.split("-");
                            if (parts.length >= 2) {
                                return parts[0] + "-" + parts[1];
                            }
                        }
                        return zone;
                    }
                }
            }
        } catch (Exception e) {
            // 리소스 데이터 접근 실패 시 무시
        }
        return null;
    }

    private String extractStatus(ResourceSearchResult result) {
        // ResourceSearchResult의 additionalAttributes에서 상태 정보 추출
        try {
            Struct additionalAttributes = result.getAdditionalAttributes();
            if (additionalAttributes != null) {
                Map<String, Value> fields = additionalAttributes.getFieldsMap();
                if (fields.containsKey("state")) {
                    Value stateValue = fields.get("state");
                    if (stateValue.hasStringValue()) {
                        return stateValue.getStringValue();
                    }
                }
                if (fields.containsKey("status")) {
                    Value statusValue = fields.get("status");
                    if (statusValue.hasStringValue()) {
                        return statusValue.getStringValue();
                    }
                }
            }
        } catch (Exception e) {
            // 리소스 데이터 접근 실패 시 무시
        }
        return null;
    }

    private GcpResourceResponse convertToResponse(GcpResource resource) {
        GcpResourceResponse response = new GcpResourceResponse();
        response.setResourceName(resource.getResourceName());
        response.setResourceType(resource.getResourceType());
        response.setMonthlyCost(resource.getMonthlyCost());
        response.setRegion(resource.getRegion());
        response.setLastUpdated(resource.getLastUpdated());
        return response;
    }
}

