package com.budgetops.backend.billing.repository;

import com.budgetops.backend.billing.entity.Workspace;
import com.budgetops.backend.domain.user.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByOwner(Member owner);

    List<Workspace> findByMembersContaining(Member member);
}
