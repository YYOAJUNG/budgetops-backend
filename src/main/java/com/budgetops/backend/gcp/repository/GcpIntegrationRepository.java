package com.budgetops.backend.gcp.repository;

import com.budgetops.backend.gcp.entity.GcpIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GcpIntegrationRepository extends JpaRepository<GcpIntegration, Long> {
    Optional<GcpIntegration> findByServiceAccountId(String serviceAccountId);
    List<GcpIntegration> findByBillingAccountId(String billingAccountId);
}


