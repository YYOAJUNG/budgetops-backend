package com.budgetops.backend.gcp.repository;

import com.budgetops.backend.gcp.entity.GcpResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GcpResourceRepository extends JpaRepository<GcpResource, Long> {


}

