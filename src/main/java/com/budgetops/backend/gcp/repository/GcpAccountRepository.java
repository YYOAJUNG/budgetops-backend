package com.budgetops.backend.gcp.repository;

import com.budgetops.backend.gcp.entity.GcpAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GcpAccountRepository extends JpaRepository<GcpAccount, Long> {
    Optional<GcpAccount> findByServiceAccountId(String serviceAccountId);
    List<GcpAccount> findByBillingAccountId(String billingAccountId);
    List<GcpAccount> findByOwnerId(Long ownerId);
    Optional<GcpAccount> findByIdAndOwnerId(Long id, Long ownerId);
}


