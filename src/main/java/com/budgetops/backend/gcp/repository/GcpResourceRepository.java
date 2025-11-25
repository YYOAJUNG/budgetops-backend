package com.budgetops.backend.gcp.repository;

import com.budgetops.backend.gcp.entity.GcpResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GcpResourceRepository extends JpaRepository<GcpResource, Long> {
    Optional<GcpResource> findByResourceIdAndGcpAccountId(String resourceId, Long gcpAccountId);
    List<GcpResource> findByGcpAccountId(Long gcpAccountId);
    Optional<GcpResource> findByResourceId(String resourceId);
}

