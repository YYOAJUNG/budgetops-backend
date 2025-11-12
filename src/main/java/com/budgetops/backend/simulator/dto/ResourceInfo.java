package com.budgetops.backend.simulator.dto;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

/**
 * 공통 리소스 정보 모델
 */
@Value
@Builder
public class ResourceInfo {
    String id;
    String csp;  // AWS, GCP, Azure
    String service;  // EC2, GCE, VM, S3, GCS, etc.
    String region;
    String project;  // 프로젝트/워크스페이스
    Map<String, String> tags;  // env, owner, etc.
}

