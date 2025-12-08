package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.entity.GcpAccount;
import com.budgetops.backend.gcp.entity.GcpResource;
import com.budgetops.backend.gcp.repository.GcpResourceRepository;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesSettings;
import com.google.cloud.compute.v1.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class GcpResourceControlService {

    private final GcpResourceRepository resourceRepository;

    /**
     * GCP Compute Engine 인스턴스 시작
     * 
     * @param accountId GCP 계정 ID
     * @param resourceId 리소스 ID (GCP 인스턴스 ID)
     * @param memberId 멤버 ID (권한 검증용)
     */
    @Transactional
    public void startInstance(Long accountId, String resourceId, Long memberId) {
        GcpResource resource = getValidatedResource(resourceId, accountId, memberId);
        GcpAccount account = resource.getGcpAccount();
        
        String instanceName = extractInstanceName(resource);
        String zone = extractZone(resource);
        String projectId = account.getProjectId();

        log.info("Starting GCP instance {} in zone {} for account {}", instanceName, zone, accountId);

        ServiceAccountCredentials credentials = GcpCredentialParser.parse(account.getEncryptedServiceAccountKey());

        try {
            InstancesSettings settings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            
            try (InstancesClient instancesClient = InstancesClient.create(settings)) {
                OperationFuture<Operation, Operation> operationFuture = instancesClient.startAsync(projectId, zone, instanceName);
                
                // 비동기 작업이므로 완료를 기다리지 않음
                log.info("Successfully initiated start operation for instance {}: operation {}", 
                        instanceName, operationFuture.getName());
            }
        } catch (IOException e) {
            log.error("Failed to start GCP instance {}: {}", instanceName, e.getMessage(), e);
            throw new IllegalStateException("GCP 인스턴스 시작 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while starting GCP instance {}", instanceName, e);
            throw new RuntimeException("GCP 인스턴스 시작 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * GCP Compute Engine 인스턴스 정지
     * 
     * @param accountId GCP 계정 ID
     * @param resourceId 리소스 ID (GCP 인스턴스 ID)
     * @param memberId 멤버 ID (권한 검증용)
     */
    @Transactional
    public void stopInstance(Long accountId, String resourceId, Long memberId) {
        GcpResource resource = getValidatedResource(resourceId, accountId, memberId);
        GcpAccount account = resource.getGcpAccount();
        
        String instanceName = extractInstanceName(resource);
        String zone = extractZone(resource);
        String projectId = account.getProjectId();

        log.info("Stopping GCP instance {} in zone {} for account {}", instanceName, zone, accountId);

        ServiceAccountCredentials credentials = GcpCredentialParser.parse(account.getEncryptedServiceAccountKey());

        try {
            InstancesSettings settings = InstancesSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            
            try (InstancesClient instancesClient = InstancesClient.create(settings)) {
                OperationFuture<Operation, Operation> operationFuture = instancesClient.stopAsync(projectId, zone, instanceName);
                
                // 비동기 작업이므로 완료를 기다리지 않음
                log.info("Successfully initiated stop operation for instance {}: operation {}", 
                        instanceName, operationFuture.getName());
            }
        } catch (IOException e) {
            log.error("Failed to stop GCP instance {}: {}", instanceName, e.getMessage(), e);
            throw new IllegalStateException("GCP 인스턴스 정지 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while stopping GCP instance {}", instanceName, e);
            throw new RuntimeException("GCP 인스턴스 정지 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 리소스 검증 및 조회
     */
    private GcpResource getValidatedResource(String resourceId, Long accountId, Long memberId) {
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

        // 계정 ID 일치 확인
        if (!account.getId().equals(accountId)) {
            throw new ResponseStatusException(NOT_FOUND, "GCP 리소스를 찾을 수 없습니다: " + resourceId);
        }

        // 권한 검증
        if (account.getOwner() == null || !account.getOwner().getId().equals(memberId)) {
            throw new ResponseStatusException(NOT_FOUND, "GCP 리소스를 찾을 수 없습니다: " + resourceId);
        }

        String serviceAccountKeyJson = account.getEncryptedServiceAccountKey();
        if (serviceAccountKeyJson == null || serviceAccountKeyJson.isBlank()) {
            throw new IllegalStateException("서비스 계정 키가 설정되지 않았습니다.");
        }

        return resource;
    }

    /**
     * GcpResource에서 인스턴스 이름 추출
     */
    private String extractInstanceName(GcpResource resource) {
        // resourceName 사용 (이미 인스턴스 이름으로 저장되어 있음)
        if (resource.getResourceName() != null && !resource.getResourceName().isEmpty()) {
            return resource.getResourceName();
        }

        // resourceId에서 추출 시도
        // 예: "//compute.googleapis.com/projects/my-project/zones/us-central1-a/instances/my-instance"
        String resourceId = resource.getResourceId();
        if (resourceId != null && resourceId.contains("/instances/")) {
            String[] parts = resourceId.split("/instances/");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        throw new IllegalStateException("인스턴스 이름을 추출할 수 없습니다. resourceId: " + resourceId);
    }

    /**
     * GcpResource에서 zone 추출
     */
    private String extractZone(GcpResource resource) {
        // additionalAttributes에서 zone 추출 시도
        Map<String, Object> additionalAttributes = resource.getAdditionalAttributes();
        if (additionalAttributes != null && additionalAttributes.containsKey("zone")) {
            Object zoneObj = additionalAttributes.get("zone");
            if (zoneObj instanceof String) {
                String zone = (String) zoneObj;
                // zone이 이미 "us-central1-a" 형식이면 그대로 사용
                if (!zone.contains("/")) {
                    return zone;
                }
                // zone이 "projects/my-project/zones/us-central1-a" 형식이면 zone 이름만 추출
                if (zone.contains("/zones/")) {
                    String[] parts = zone.split("/zones/");
                    if (parts.length > 1) {
                        return parts[1];
                    }
                }
                return zone;
            }
        }

        // resourceId에서 zone 추출 시도
        // 예: "//compute.googleapis.com/projects/my-project/zones/us-central1-a/instances/my-instance"
        String resourceId = resource.getResourceId();
        if (resourceId != null && resourceId.contains("/zones/") && resourceId.contains("/instances/")) {
            String[] parts = resourceId.split("/zones/");
            if (parts.length > 1) {
                String afterZones = parts[1];
                String[] instanceParts = afterZones.split("/instances/");
                if (instanceParts.length > 0) {
                    return instanceParts[0];
                }
            }
        }

        throw new IllegalStateException("Zone을 추출할 수 없습니다. resourceId: " + resourceId);
    }
}

