package com.budgetops.backend.domain.user.service;

import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.service.AwsAccountService;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.azure.service.AzureAccountService;
import com.budgetops.backend.billing.service.BillingService;
import com.budgetops.backend.billing.service.PaymentService;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.gcp.service.GcpAccountService;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BillingService billingService;
    private final PaymentService paymentService;

    private final AwsAccountRepository awsAccountRepository;
    private final AwsAccountService awsAccountService;

    private final AzureAccountRepository azureAccountRepository;
    private final AzureAccountService azureAccountService;

    private final GcpAccountRepository gcpAccountRepository;
    private final GcpAccountService gcpAccountService;

    private final NcpAccountRepository ncpAccountRepository;

    /**
     * OAuth 로그인 시 Member upsert
     * - 기존 회원: name 업데이트
     * - 신규 회원: insert
     */
    @Transactional
    public Member upsertOAuthMember(String email, String name) {
        return memberRepository.findByEmail(email)
                .map(existingMember -> {
                    // 기존 회원 - name 업데이트 및 마지막 로그인 시간 업데이트
                    log.info("Existing member login: email={}, id={}", email, existingMember.getId());
                    existingMember.setName(name);
                    existingMember.setLastLoginAt(LocalDateTime.now());
                    return memberRepository.save(existingMember);
                })
                .orElseGet(() -> {
                    // 신규 회원 - insert
                    Member newMember = Member.builder()
                            .email(email)
                            .name(name)
                            .lastLoginAt(LocalDateTime.now()) // 신규 회원도 로그인 시간 설정
                            .build();
                    Member savedMember = memberRepository.save(newMember);
                    billingService.initializeBilling(savedMember);
                    log.info("New member created with default billing: email={}, id={}", email, savedMember.getId());
                    return savedMember;
                });
    }

    /**
     * 회원 탈퇴
     * - CSP 계정(AWS/Azure/GCP/NCP) 및 관련 리소스/빌링/결제 정보를 모두 정리한 뒤 Member 삭제
     */
    @Transactional
    public void deleteMemberWithAssociations(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member를 찾을 수 없습니다: " + memberId));

        log.info("Deleting member {} and all associated resources", memberId);

        // 1. AWS 계정 및 리소스 삭제
        awsAccountRepository.findByOwnerIdAndActiveTrue(memberId)
                .forEach(account -> {
                    try {
                        awsAccountService.deactivateAccount(account.getId(), memberId);
                    } catch (Exception e) {
                        log.warn("Failed to delete AWS account {} for member {}: {}", account.getId(), memberId, e.getMessage());
                    }
                });

        // 2. Azure 계정 삭제
        azureAccountRepository.findByOwnerIdAndActiveTrue(memberId)
                .forEach(account -> {
                    try {
                        azureAccountService.deactivateAccount(account.getId(), memberId);
                    } catch (Exception e) {
                        log.warn("Failed to delete Azure account {} for member {}: {}", account.getId(), memberId, e.getMessage());
                    }
                });

        // 3. GCP 계정 및 리소스 삭제
        gcpAccountRepository.findByOwnerId(memberId)
                .forEach(account -> {
                    try {
                        gcpAccountService.deleteAccount(account.getId(), memberId);
                    } catch (Exception e) {
                        log.warn("Failed to delete GCP account {} for member {}: {}", account.getId(), memberId, e.getMessage());
                    }
                });

        // 4. NCP 계정 삭제 (active 여부와 관계없이 모두 삭제)
        ncpAccountRepository.findByOwnerId(memberId)
                .forEach(account -> {
                    try {
                        ncpAccountRepository.delete(account);
                        log.info("Deleted NCP account {} for member {}", account.getId(), memberId);
                    } catch (Exception e) {
                        log.warn("Failed to delete NCP account {} for member {}: {}", account.getId(), memberId, e.getMessage());
                    }
                });

        // 5. 빌링/결제 정보 삭제
        try {
            paymentService.deletePayment(member);
        } catch (Exception e) {
            log.warn("Failed to delete payment info for member {}: {}", memberId, e.getMessage());
        }

        try {
            billingService.deleteBilling(member);
        } catch (Exception e) {
            log.warn("Failed to delete billing info for member {}: {}", memberId, e.getMessage());
        }

        // 6. Member 삭제
        memberRepository.delete(member);
        log.info("Successfully deleted member {}", memberId);
    }
}
