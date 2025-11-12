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
import com.google.cloud.asset.v1.VersionedResource;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
            
            // TODO: AssetType 추가 필요 (주요 서비스 위주로. 현재는 VM 인스턴스만 되어 있음)
            try (AssetServiceClient client = AssetServiceClient.create(settings)) {
                String parent = "projects/" + projectId;
                SearchAllResourcesRequest request = SearchAllResourcesRequest.newBuilder()
                        .setScope(parent)
                        .addAssetTypes("compute.googleapis.com/Instance")
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
                existingResource.setAdditionalAttributes(resource.getAdditionalAttributes());
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
        response.setAccountName(account.getName());
        response.setProjectId(account.getProjectId());
        response.setResources(responseList);
        return response;
    }

    @Transactional
    public List<GcpResourceListResponse> listAllAccountsResources() {
        List<GcpAccount> accounts = accountRepository.findAll();
        List<GcpResourceListResponse> responses = new ArrayList<>();
        
        for (GcpAccount account : accounts) {
            try {
                // 각 계정에 대해 GCP API를 호출하여 리소스 조회
                GcpResourceListResponse response = listResources(account.getId());
                responses.add(response);
            } catch (Exception e) {
                // 특정 계정의 리소스 조회 실패 시에도 다른 계정은 계속 조회
                // 빈 리소스 목록으로 응답 추가
                GcpResourceListResponse response = new GcpResourceListResponse();
                response.setAccountId(account.getId());
                response.setAccountName(account.getName());
                response.setProjectId(account.getProjectId());
                response.setResources(new ArrayList<>());
                responses.add(response);
            }
        }
        
        return responses;
    }

    private GcpResource convertResourceSearchResultToResource(ResourceSearchResult result, GcpAccount account, Instant now) {
        // additionalAttributes에서 id 추출 (GCP 리소스의 실제 ID)
        String resourceId = extractResourceIdFromAdditionalAttributes(result);
        // id가 없으면 name을 fallback으로 사용
        if (resourceId == null || resourceId.isEmpty()) {
            resourceId = result.getName();
        }
        
        String resourceType = result.getAssetType();
        String resourceName = extractResourceName(result);
        String region = extractRegion(result);
        String status = extractStatus(result);
        String description = null;
        
        // 리소스 타입별 추가 정보 추출
        Map<String, Object> additionalAttributes = extractAdditionalAttributes(result, resourceType);

        return GcpResource.builder()
                .resourceId(resourceId)
                .resourceType(resourceType)
                .resourceName(resourceName)
                .region(region)
                .status(status)
                .description(description)
                .monthlyCost(null) // 나중에 별도 API로 채워질 예정
                .lastUpdated(now)
                .additionalAttributes(additionalAttributes)
                .gcpAccount(account)
                .build();
    }
    
    private Map<String, Object> extractAdditionalAttributes(ResourceSearchResult result, String resourceType) {
        Map<String, Object> attributes = new HashMap<>();
        
        try {
            Struct additionalAttributes = result.getAdditionalAttributes();
            if (additionalAttributes == null) {
                return attributes;
            }
            
            Map<String, Value> fields = additionalAttributes.getFieldsMap();
            
            // Compute Instance의 경우 특정 필드 추출
            if ("compute.googleapis.com/Instance".equals(resourceType)) {
                // machineType 추출
                if (fields.containsKey("machineType")) {
                    Value machineTypeValue = fields.get("machineType");
                    if (machineTypeValue.hasStringValue()) {
                        attributes.put("machineType", machineTypeValue.getStringValue());
                    }
                }
                
                // externalIPs 추출
                if (fields.containsKey("externalIPs")) {
                    Value externalIPsValue = fields.get("externalIPs");
                    if (externalIPsValue.hasListValue()) {
                        List<Object> externalIPs = new ArrayList<>();
                        for (Value ipValue : externalIPsValue.getListValue().getValuesList()) {
                            if (ipValue.hasStringValue()) {
                                externalIPs.add(ipValue.getStringValue());
                            }
                        }
                        attributes.put("externalIPs", externalIPs);
                    }
                }
                
                // internalIPs 추출
                if (fields.containsKey("internalIPs")) {
                    Value internalIPsValue = fields.get("internalIPs");
                    if (internalIPsValue.hasListValue()) {
                        List<Object> internalIPs = new ArrayList<>();
                        for (Value ipValue : internalIPsValue.getListValue().getValuesList()) {
                            if (ipValue.hasStringValue()) {
                                internalIPs.add(ipValue.getStringValue());
                            }
                        }
                        attributes.put("internalIPs", internalIPs);
                    }
                }
            }
            
            // 다른 리소스 타입이 추가될 경우 여기에 추가
            // 예: if ("storage.googleapis.com/Bucket".equals(resourceType)) { ... }
            
        } catch (Exception e) {
            // additionalAttributes 접근 실패 시 빈 Map 반환
        }
        
        return attributes;
    }
    
    private String extractResourceIdFromAdditionalAttributes(ResourceSearchResult result) {
        try {
            Struct additionalAttributes = result.getAdditionalAttributes();
            if (additionalAttributes != null) {
                Map<String, Value> fields = additionalAttributes.getFieldsMap();
                if (fields.containsKey("id")) {
                    Value idValue = fields.get("id");
                    if (idValue.hasStringValue()) {
                        return idValue.getStringValue();
                    }
                }
            }
        } catch (Exception e) {
            // additionalAttributes 접근 실패 시 무시
        }
        return null;
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
        // 1. ResourceSearchResult의 최상위 레벨에서 state 필드 추출 (GCP API는 state 필드를 최상위에 제공)
        try {
            String state = result.getState();
            if (state != null && !state.isEmpty()) {
                return state;
            }
        } catch (Exception e) {
            // getState() 접근 실패 시 무시하고 계속
        }

        // 2. versionedResources에서 state 필드 추출
        try {
            List<VersionedResource> versionedResources = result.getVersionedResourcesList();
            if (versionedResources != null && !versionedResources.isEmpty()) {
                // 가장 최신 버전의 리소스 사용
                VersionedResource latestResource = versionedResources.get(0);
                if (latestResource.hasResource()) {
                    Struct resourceData = latestResource.getResource();
                    if (resourceData != null) {
                        Map<String, Value> fields = resourceData.getFieldsMap();
                        // state 필드 확인
                        if (fields.containsKey("state")) {
                            Value stateValue = fields.get("state");
                            if (stateValue.hasStringValue()) {
                                return stateValue.getStringValue();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // versionedResources 접근 실패 시 무시하고 계속
        }

        // 3. additionalAttributes에서 상태 정보 추출
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
            }
        } catch (Exception e) {
            // additionalAttributes 접근 실패 시 무시
        }
        return null;
    }

    private GcpResourceResponse convertToResponse(GcpResource resource) {
        GcpResourceResponse response = new GcpResourceResponse();
        response.setId(resource.getId()); // 우리 서비스 내부 ID
        response.setResourceId(resource.getResourceId()); // GCP API의 additionalAttributes.id
        response.setResourceName(resource.getResourceName());
        response.setResourceType(resource.getResourceType());
        response.setResourceTypeShort(extractResourceTypeShort(resource.getResourceType()));
        response.setMonthlyCost(resource.getMonthlyCost());
        response.setRegion(resource.getRegion());
        response.setStatus(resource.getStatus());
        response.setLastUpdated(resource.getLastUpdated());
        response.setAdditionalAttributes(resource.getAdditionalAttributes());
        return response;
    }

    private String extractResourceTypeShort(String resourceType) {
        // 예: "compute.googleapis.com/Instance" -> "Instance"
        if (resourceType == null || resourceType.isEmpty()) {
            return null;
        }
        int lastSlashIndex = resourceType.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < resourceType.length() - 1) {
            return resourceType.substring(lastSlashIndex + 1);
        }
        return resourceType;
    }
}

