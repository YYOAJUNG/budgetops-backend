package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.entity.AwsResource;
import com.budgetops.backend.aws.repository.AwsResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AwsResourceQueryService {
    private final AwsResourceRepository resourceRepository;

    @Transactional(readOnly = true)
    public List<AwsResource> findByAccount(Long accountId) {
        return resourceRepository.findByAwsAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<AwsResource> findByType(String resourceType) {
        return resourceRepository.findByResourceType(resourceType);
    }

    @Transactional(readOnly = true)
    public List<AwsResource> findByAccountAndType(Long accountId, String resourceType) {
        return resourceRepository.findByAwsAccountIdAndResourceType(accountId, resourceType);
    }
}


