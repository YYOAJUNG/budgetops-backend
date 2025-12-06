package com.budgetops.backend.admin.service;

import com.budgetops.backend.admin.dto.UserListResponse;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.repository.BillingRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final MemberRepository memberRepository;
    private final BillingRepository billingRepository;
    private final AwsAccountRepository awsAccountRepository;
    private final AzureAccountRepository azureAccountRepository;
    private final GcpAccountRepository gcpAccountRepository;
    private final NcpAccountRepository ncpAccountRepository;

    /**
     * 사용자 목록 조회 (페이지네이션) - 모든 상세 정보 포함
     */
    @Transactional(readOnly = true)
    public Page<UserListResponse> getUserList(Pageable pageable) {
        Page<Member> members = memberRepository.findAll(pageable);
        
        return members.map(member -> {
            Billing billing = billingRepository.findByMember(member)
                    .orElse(null);
            
            Long userId = member.getId();
            // 클라우드 계정 개수 집계
            long awsCount = awsAccountRepository.countByOwnerId(userId);
            long azureCount = azureAccountRepository.countByOwnerId(userId);
            long gcpCount = gcpAccountRepository.countByOwnerId(userId);
            long ncpCount = ncpAccountRepository.countByOwnerId(userId);
            int totalCloudAccountCount = (int) (awsCount + azureCount + gcpCount + ncpCount);
            
            return UserListResponse.builder()
                    .id(member.getId())
                    .email(member.getEmail())
                    .name(member.getName())
                    .createdAt(member.getCreatedAt())
                    .billingPlan(billing != null ? billing.getCurrentPlan().name() : "FREE")
                    .currentTokens(billing != null ? billing.getCurrentTokens() : 0)
                    .cloudAccountCount(totalCloudAccountCount)
                    .awsAccountCount((int) awsCount)
                    .azureAccountCount((int) azureCount)
                    .gcpAccountCount((int) gcpCount)
                    .ncpAccountCount((int) ncpCount)
                    .build();
        });
    }

    /**
     * 전체 사용자의 결제 내역 조회
     */

    /**
     * 관리자 권한으로 토큰 추가 부여
     */
}

