package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.dto.GcpResourceListResponse;
import com.budgetops.backend.gcp.dto.GcpResourceMetricsResponse;
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
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import com.google.monitoring.v3.Aggregation;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class GcpResourceService {
    private final GcpResourceRepository resourceRepository;
    private final GcpAccountRepository accountRepository;

    @Transactional
    public GcpResourceListResponse listResources(Long accountId, Long memberId) {
        GcpAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
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
    public List<GcpResourceListResponse> listAllAccountsResources(Long memberId) {
        List<GcpAccount> accounts = accountRepository.findByOwnerId(memberId);
        List<GcpResourceListResponse> responses = new ArrayList<>();
        
        for (GcpAccount account : accounts) {
            try {
                // 각 계정에 대해 GCP API를 호출하여 리소스 조회
                GcpResourceListResponse response = listResources(account.getId(), memberId);
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
                
                // zone 추출
                if (fields.containsKey("zone")) {
                    Value zoneValue = fields.get("zone");
                    if (zoneValue.hasStringValue()) {
                        String zone = zoneValue.getStringValue();
                        // zone에서 zone 이름만 추출 (예: "projects/my-project/zones/us-central1-a" -> "us-central1-a")
                        if (zone.contains("/zones/")) {
                            String[] parts = zone.split("/zones/");
                            if (parts.length > 1) {
                                attributes.put("zone", parts[1]);
                            } else {
                                attributes.put("zone", zone);
                            }
                        } else {
                            attributes.put("zone", zone);
                        }
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

    /**
     * GCP 리소스의 메트릭 조회
     * 
     * @param resourceId 리소스 ID (GCP 인스턴스 ID)
     * @param hours 조회할 시간 범위 (기본값: 1시간)
     * @return 메트릭 데이터 (CPU, NetworkIn, NetworkOut, MemoryUtilization)
     */
    public GcpResourceMetricsResponse getResourceMetrics(String resourceId, Long memberId, Integer hours) {
        // 리소스 조회
        GcpResource resource = resourceRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "GCP 리소스를 찾을 수 없습니다: " + resourceId));

        // resourceType 검증 - compute.googleapis.com/Instance만 허용
        if (!"compute.googleapis.com/Instance".equals(resource.getResourceType())) {
            throw new IllegalArgumentException("현재는 compute.googleapis.com/Instance 리소스 타입만 지원합니다.");
        }

        GcpAccount account = resource.getGcpAccount();
        if (account == null) {
            throw new IllegalStateException("리소스에 연결된 GCP 계정을 찾을 수 없습니다.");
        }

        if (account.getOwner() == null || !account.getOwner().getId().equals(memberId)) {
            throw new ResponseStatusException(NOT_FOUND, "GCP 리소스를 찾을 수 없습니다: " + resourceId);
        }

        String serviceAccountKeyJson = account.getEncryptedServiceAccountKey();
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            throw new IllegalStateException("서비스 계정 키가 설정되지 않았습니다.");
        }

        ServiceAccountCredentials credentials = GcpCredentialParser.parse(serviceAccountKeyJson);
        String projectId = account.getProjectId();

        int hoursToQuery = hours != null && hours > 0 ? hours : 1;
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(hoursToQuery, ChronoUnit.HOURS);

        log.info("Fetching metrics for GCP resource {} in project {} for the last {} hours",
                resourceId, projectId, hoursToQuery);

        // GCP Monitoring API를 사용하여 메트릭 조회
        try {
            MetricServiceSettings settings = MetricServiceSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();

            try (MetricServiceClient client = MetricServiceClient.create(settings)) {
                String projectName = ProjectName.of(projectId).toString();

                // CPU Utilization
                List<GcpResourceMetricsResponse.MetricDataPoint> cpuMetrics = getMetricData(
                        client, projectName, resourceId,
                        "compute.googleapis.com/instance/cpu/utilization", "Percent",
                        startTime, endTime);

                // Network In
                List<GcpResourceMetricsResponse.MetricDataPoint> networkInMetrics = getMetricData(
                        client, projectName, resourceId,
                        "compute.googleapis.com/instance/network/received_bytes_count", "Bytes",
                        startTime, endTime);

                // Network Out
                List<GcpResourceMetricsResponse.MetricDataPoint> networkOutMetrics = getMetricData(
                        client, projectName, resourceId,
                        "compute.googleapis.com/instance/network/sent_bytes_count", "Bytes",
                        startTime, endTime);

                // Memory Utilization (Monitoring Agent가 설치된 경우에만 사용 가능)
                List<GcpResourceMetricsResponse.MetricDataPoint> memoryMetrics = getMetricData(
                        client, projectName, resourceId,
                        "agent.googleapis.com/memory/percent_used", "Percent",
                        startTime, endTime);

                return GcpResourceMetricsResponse.builder()
                        .resourceId(resourceId)
                        .resourceType(resource.getResourceType())
                        .region(resource.getRegion())
                        .cpuUtilization(cpuMetrics)
                        .networkIn(networkInMetrics)
                        .networkOut(networkOutMetrics)
                        .memoryUtilization(memoryMetrics)
                        .build();
            }
        } catch (IOException e) {
            log.error("Failed to create MetricServiceClient: {}", e.getMessage(), e);
            throw new RuntimeException("메트릭 조회 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while fetching metrics for resource {}", resourceId, e);
            throw new RuntimeException("메트릭 조회 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * GCP Monitoring API에서 메트릭 데이터 조회
     */
    private List<GcpResourceMetricsResponse.MetricDataPoint> getMetricData(
            MetricServiceClient client,
            String projectName,
            String resourceId,
            String metricType,
            String unit,
            Instant startTime,
            Instant endTime) {
        try {
            TimeInterval interval = TimeInterval.newBuilder()
                    .setStartTime(Timestamp.newBuilder()
                            .setSeconds(startTime.getEpochSecond())
                            .setNanos(startTime.getNano())
                            .build())
                    .setEndTime(Timestamp.newBuilder()
                            .setSeconds(endTime.getEpochSecond())
                            .setNanos(endTime.getNano())
                            .build())
                    .build();

            // GCP Monitoring API 필터: 리소스 타입과 인스턴스 ID로 필터링
            String filter = String.format(
                    "resource.type=\"gce_instance\" AND resource.labels.instance_id=\"%s\" AND metric.type=\"%s\"",
                    resourceId, metricType);

            Aggregation aggregation = Aggregation.newBuilder()
                    .setAlignmentPeriod(com.google.protobuf.Duration.newBuilder().setSeconds(300).build()) // 5분 간격
                    .setPerSeriesAligner(Aggregation.Aligner.ALIGN_MEAN)
                    .build();

            ListTimeSeriesRequest request = ListTimeSeriesRequest.newBuilder()
                    .setName(projectName)
                    .setFilter(filter)
                    .setInterval(interval)
                    .setAggregation(aggregation)
                    .build();

            List<GcpResourceMetricsResponse.MetricDataPoint> dataPoints = new ArrayList<>();
            for (TimeSeries timeSeries : client.listTimeSeries(request).iterateAll()) {
                for (Point point : timeSeries.getPointsList()) {
                    TypedValue value = point.getValue();
                    double doubleValue = 0.0;

                    if (value.hasDoubleValue()) {
                        doubleValue = value.getDoubleValue();
                    } else if (value.hasInt64Value()) {
                        doubleValue = (double) value.getInt64Value();
                    }

                    Timestamp timestamp = point.getInterval().getEndTime();
                    String timestampStr = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos())
                            .toString();

                    dataPoints.add(GcpResourceMetricsResponse.MetricDataPoint.builder()
                            .timestamp(timestampStr)
                            .value(doubleValue)
                            .unit(unit)
                            .build());
                }
            }

            return dataPoints.stream()
                    .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            // 메트릭이 없는 경우 (예: MemoryUtilization은 Monitoring Agent 필요)
            log.warn("Metric {} not available for resource {}: {}", metricType, resourceId, e.getMessage());
            return new ArrayList<>();
        }
    }
}

