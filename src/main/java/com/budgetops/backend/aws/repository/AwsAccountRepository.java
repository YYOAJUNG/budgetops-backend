package com.budgetops.backend.aws.repository;

import com.budgetops.backend.aws.entity.AwsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AwsAccountRepository extends JpaRepository<AwsAccount, Long> {
    Optional<AwsAccount> findByAccountId(String accountId);
    List<AwsAccount> findByIsActiveTrue();
    Optional<AwsAccount> findByAccessKeyId(String accessKeyId);
}
