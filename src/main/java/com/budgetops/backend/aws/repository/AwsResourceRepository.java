package com.budgetops.backend.aws.repository;

import com.budgetops.backend.aws.entity.AwsResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AwsResourceRepository extends JpaRepository<AwsResource, Long> {
    List<AwsResource> findByAwsAccountId(Long awsAccountId);
    List<AwsResource> findByResourceType(String resourceType);
    List<AwsResource> findByAwsAccountIdAndResourceType(Long awsAccountId, String resourceType);
}
