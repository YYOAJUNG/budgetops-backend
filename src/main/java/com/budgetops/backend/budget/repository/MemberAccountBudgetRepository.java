package com.budgetops.backend.budget.repository;

import com.budgetops.backend.budget.entity.MemberAccountBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberAccountBudgetRepository extends JpaRepository<MemberAccountBudget, Long> {

    List<MemberAccountBudget> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);
}


