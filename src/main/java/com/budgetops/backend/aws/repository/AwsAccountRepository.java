package com.budgetops.backend.aws.repository;

import com.budgetops.backend.aws.entity.AwsAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AwsAccountRepository extends JpaRepository<AwsAccount, Long> {
    Optional<AwsAccount> findByAccessKeyId(String accessKeyId);
    List<AwsAccount> findByActiveTrue();
    List<AwsAccount> findByOwnerIdAndActiveTrue(Long ownerId);
    Optional<AwsAccount> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);
}
