package com.budgetops.backend.aws.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AwsEc2InstanceCreateRequest {
    @NotBlank(message = "인스턴스 이름은 필수입니다.")
    private String name;
    
    @NotBlank(message = "인스턴스 타입은 필수입니다.")
    private String instanceType; // 예: t3.micro, t3.small
    
    @NotBlank(message = "AMI ID는 필수입니다.")
    private String imageId; // 예: ami-0c55b159cbfafe1f0
    
    private String keyPairName; // 선택사항: SSH 키 페어 이름
    
    private String securityGroupId; // 선택사항: 보안 그룹 ID
    
    private String subnetId; // 선택사항: 서브넷 ID
    
    private String availabilityZone; // 선택사항: 가용 영역
    
    private String userData; // 선택사항: 사용자 데이터 스크립트
    
    private Integer minCount = 1; // 최소 인스턴스 수 (기본값: 1)
    
    private Integer maxCount = 1; // 최대 인스턴스 수 (기본값: 1)
}

