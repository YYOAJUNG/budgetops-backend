package com.budgetops.backend.budget.repository;

import com.budgetops.backend.budget.entity.AccountBudgetSetting;
import com.budgetops.backend.budget.model.CloudProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountBudgetSettingRepository extends JpaRepository<AccountBudgetSetting, Long> {

    List<AccountBudgetSetting> findByMemberId(Long memberId);

    Optional<AccountBudgetSetting> findByMemberIdAndProviderAndProviderAccountId(
            Long memberId,
            CloudProvider provider,
            Long providerAccountId
    );

    void deleteByMemberIdAndIdNotIn(Long memberId, List<Long> ids);
}

