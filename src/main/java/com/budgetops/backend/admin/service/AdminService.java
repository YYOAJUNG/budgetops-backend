package com.budgetops.backend.admin.service;

import com.budgetops.backend.admin.dto.AdminPaymentHistoryResponse;
import com.budgetops.backend.admin.dto.UserListResponse;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.billing.constants.TokenConstants;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.repository.BillingRepository;
import com.budgetops.backend.billing.repository.PaymentRepository;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final MemberRepository memberRepository;
    private final BillingRepository billingRepository;
    private final PaymentRepository paymentRepository;
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
    @Transactional(readOnly = true)
    public List<AdminPaymentHistoryResponse> getAllPaymentHistory() {
        List<Payment> payments = paymentRepository.findAll();
        
        return payments.stream()
                .map(payment -> {
                    Member member = payment.getMember();
                    // Payment 엔티티에는 결제 타입 정보가 없으므로 기본값으로 설정
                    // 나중에 PaymentHistory 엔티티를 추가하면 실제 타입을 구분할 수 있음
                    String paymentType = "MEMBERSHIP"; // 기본값
                    
                    return AdminPaymentHistoryResponse.builder()
                            .id(payment.getId())
                            .userId(member.getId())
                            .userEmail(member.getEmail())
                            .userName(member.getName())
                            .paymentType(paymentType)
                            .impUid(payment.getImpUid())
                            .amount(null) // Payment 엔티티에 amount 필드가 없음
                            .status(payment.getStatus().name())
                            .createdAt(payment.getCreatedAt())
                            .lastVerifiedAt(payment.getLastVerifiedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 관리자 권한으로 토큰 추가 부여
     */
    @Transactional
    public int grantTokens(Long userId, Integer tokens, String reason) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        
        Billing billing = billingRepository.findByMember(member)
                .orElseThrow(() -> new BillingNotFoundException(userId));
        
        int currentTokens = billing.getCurrentTokens();
        int newTotalTokens = currentTokens + tokens;
        
        // 최대 토큰 보유량 체크
        if (newTotalTokens > TokenConstants.MAX_TOKEN_LIMIT) {
            int availableSpace = TokenConstants.MAX_TOKEN_LIMIT - currentTokens;
            throw new IllegalStateException(
                    String.format("토큰 보유량 한도를 초과할 수 없습니다. (현재: %d, 추가: %d, 최대: %d, 추가 가능: %d)",
                            currentTokens, tokens, TokenConstants.MAX_TOKEN_LIMIT, availableSpace)
            );
        }
        
        // 토큰 추가
        billing.addTokens(tokens);
        billingRepository.save(billing);
        
        log.info("관리자 토큰 부여: userId={}, tokens={}, reason={}, oldTotal={}, newTotal={}",
                userId, tokens, reason, currentTokens, billing.getCurrentTokens());
        
        return billing.getCurrentTokens();
    }
}

