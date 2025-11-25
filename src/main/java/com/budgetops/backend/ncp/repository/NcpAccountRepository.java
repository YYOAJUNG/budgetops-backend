package com.budgetops.backend.ncp.repository;

import com.budgetops.backend.ncp.entity.NcpAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NcpAccountRepository extends JpaRepository<NcpAccount, Long> {
    Optional<NcpAccount> findByAccessKey(String accessKey);
    List<NcpAccount> findByActiveTrue();

    List<NcpAccount> findByOwnerIdAndActiveTrue(Long ownerId);

    Optional<NcpAccount> findByIdAndOwnerId(Long id, Long ownerId);
}
