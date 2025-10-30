package com.budgetops.backend.gcp.repository;

import com.budgetops.backend.gcp.entity.GcpIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GcpIntegrationRepository extends JpaRepository<GcpIntegration, Long> {
}


