package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.entity.AwsResource;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.repository.AwsResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AwsResourceService {
    
    private final AwsAccountRepository awsAccountRepository;
    private final AwsResourceRepository awsResourceRepository;
    
    /**
     * 모든 활성화된 AWS 계정 조회
     */
    public List<AwsAccount> getAllActiveAccounts() {
        return awsAccountRepository.findByIsActiveTrue();
    }
    
    /**
     * 특정 AWS 계정의 모든 리소스 조회
     */
    public List<AwsResource> getResourcesByAccountId(Long accountId) {
        return awsResourceRepository.findByAwsAccountId(accountId);
    }
    
    /**
     * 특정 리소스 타입의 리소스들 조회
     */
    public List<AwsResource> getResourcesByType(String resourceType) {
        return awsResourceRepository.findByResourceType(resourceType);
    }
    
    /**
     * 특정 계정의 특정 리소스 타입 조회
     */
    public List<AwsResource> getResourcesByAccountAndType(Long accountId, String resourceType) {
        return awsResourceRepository.findByAwsAccountIdAndResourceType(accountId, resourceType);
    }
    
    /**
     * AWS 계정 ID로 계정 조회
     */
    public Optional<AwsAccount> getAccountByAccountId(String accountId) {
        return awsAccountRepository.findByAccountId(accountId);
    }
}
