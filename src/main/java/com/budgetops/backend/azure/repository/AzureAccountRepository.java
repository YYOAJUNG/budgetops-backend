package com.budgetops.backend.azure.repository;

import com.budgetops.backend.azure.entity.AzureAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AzureAccountRepository extends JpaRepository<AzureAccount, Long> {
    Optional<AzureAccount> findByClientIdAndSubscriptionId(String clientId, String subscriptionId);
    List<AzureAccount> findByActiveTrue();
    List<AzureAccount> findByOwnerIdAndActiveTrue(Long ownerId);
    Optional<AzureAccount> findByIdAndOwnerId(Long id, Long ownerId);
}

