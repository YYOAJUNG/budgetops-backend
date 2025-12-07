package com.budgetops.backend.admin.service;

import com.budgetops.backend.admin.dto.AdminPaymentHistoryResponse;
import com.budgetops.backend.admin.dto.UserListResponse;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.billing.constants.TokenConstants;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.entity.PaymentHistory;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.repository.BillingRepository;
import com.budgetops.backend.billing.repository.PaymentHistoryRepository;
import com.budgetops.backend.billing.repository.PaymentRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import com.budgetops.backend.gcp.repository.GcpAccountRepository;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final MemberRepository memberRepository;
    private final BillingRepository billingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final AwsAccountRepository awsAccountRepository;
    private final AzureAccountRepository azureAccountRepository;
    private final GcpAccountRepository gcpAccountRepository;
    private final NcpAccountRepository ncpAccountRepository;

    /**
     * 사용자 목록 조회 (페이지네이션) - 모든 상세 정보 포함
     * @param search 검색어 (이름 또는 이메일, 선택사항)
     */
    @Transactional(readOnly = true)
    public Page<UserListResponse> getUserList(Pageable pageable, String search) {
        // 기본 정렬: lastLoginAt이 있는 것 우선, 최신순
        // lastLoginAt이 없는 것들은 가입일 기준 최신순
        Sort sort = Sort.by(
            Sort.Order.asc("lastLoginAt").nullsLast(), // null이 아닌 것 우선
            Sort.Order.desc("lastLoginAt"), // 최신순
            Sort.Order.desc("createdAt") // lastLoginAt이 null인 경우 가입일 기준 최신순
        );
        
        // 사용자가 정렬을 지정했으면 그걸 사용, 아니면 기본 정렬 사용
        Pageable sortedPageable = pageable.getSort().isSorted() 
            ? pageable 
            : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        
        Page<Member> members;
        
        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = search.trim();
            members = memberRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    searchTerm, searchTerm, sortedPageable);
        } else {
            members = memberRepository.findAll(sortedPageable);
        }
        
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
                    .lastLoginAt(member.getLastLoginAt()) // 마지막 로그인 시각 추가
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
     * 전체 사용자의 결제 내역 조회 (Payment + PaymentHistory 통합)
     * @param search 검색어 (사용자 이름 또는 이메일, 선택사항)
     */
    @Transactional(readOnly = true)
    public List<AdminPaymentHistoryResponse> getAllPaymentHistory(String search) {
        List<AdminPaymentHistoryResponse> responses = new ArrayList<>();
        
        // Payment 엔티티 조회 (멤버십 결제 등록 정보)
        List<Payment> payments;
        if (search != null && !search.trim().isEmpty()) {
            payments = paymentRepository.findByMemberNameOrEmailContaining(search.trim());
        } else {
            payments = paymentRepository.findAll();
        }
        
        // Payment를 AdminPaymentHistoryResponse로 변환
        responses.addAll(payments.stream()
                .map(payment -> {
                    Member member = payment.getMember();
                    String paymentType = "MEMBERSHIP";
                    
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
                .collect(Collectors.toList()));
        
        // PaymentHistory 엔티티 조회 (토큰 구매 등 실제 결제 내역)
        List<PaymentHistory> paymentHistories;
        if (search != null && !search.trim().isEmpty()) {
            paymentHistories = paymentHistoryRepository.findByMemberNameOrEmailContaining(search.trim());
        } else {
            paymentHistories = paymentHistoryRepository.findAll();
        }
        
        // PaymentHistory를 AdminPaymentHistoryResponse로 변환
        responses.addAll(paymentHistories.stream()
                .map(history -> {
                    Member member = history.getMember();
                    // orderName에 "토큰"이 포함되어 있으면 TOKEN_PURCHASE, 아니면 MEMBERSHIP
                    String paymentType = (history.getOrderName() != null && 
                                         history.getOrderName().contains("토큰")) 
                                        ? "TOKEN_PURCHASE" 
                                        : "MEMBERSHIP";
                    
                    return AdminPaymentHistoryResponse.builder()
                            .id(history.getId())
                            .userId(member.getId())
                            .userEmail(member.getEmail())
                            .userName(member.getName())
                            .paymentType(paymentType)
                            .impUid(history.getImpUid())
                            .amount(history.getAmount())
                            .status(history.getStatus().name())
                            .createdAt(history.getCreatedAt())
                            .lastVerifiedAt(history.getPaidAt()) // PaymentHistory는 paidAt 사용
                            .build();
                })
                .collect(Collectors.toList()));
        
        // createdAt 기준으로 최신순 정렬
        responses.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        
        return responses;
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

