package com.budgetops.backend.billing.controller;

import com.budgetops.backend.billing.constants.DateConstants;
import com.budgetops.backend.billing.dto.request.PaymentRegisterRequest;
import com.budgetops.backend.billing.dto.request.TokenPurchaseRequest;
import com.budgetops.backend.billing.dto.response.PaymentHistoryResponse;
import com.budgetops.backend.billing.dto.response.PaymentResponse;
import com.budgetops.backend.billing.dto.response.TokenPurchaseResponse;
import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.billing.entity.Payment;
import com.budgetops.backend.billing.entity.Member;
import com.budgetops.backend.billing.enums.TokenPackage;
import com.budgetops.backend.billing.exception.BillingNotFoundException;
import com.budgetops.backend.billing.exception.MemberNotFoundException;
import com.budgetops.backend.billing.exception.PaymentNotFoundException;
import com.budgetops.backend.billing.repository.MemberRepository;
import com.budgetops.backend.billing.service.BillingService;
import com.budgetops.backend.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final BillingService billingService;
    private final MemberRepository memberRepository;

    private Member getMemberById(Long userId) {
        return memberRepository.findById(userId)
                .orElseThrow(() -> new MemberNotFoundException(userId));
    }

    /**
     * 결제 정보 등록/갱신
     */
    @PostMapping("/register")
    public ResponseEntity<PaymentResponse> registerPayment(
            @PathVariable Long userId,
            @RequestBody PaymentRegisterRequest request
    ) {
        Member member = getMemberById(userId);
        Payment payment = paymentService.registerPayment(request.getImpUid(), request.getCustomerUid(), member);

        log.info("결제 등록 완료: userId={}, impUid={}, customerUid={}",
                userId, request.getImpUid(), request.getCustomerUid());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    /**
     * 결제 등록 여부 확인
     */
    @GetMapping("/status")
    public ResponseEntity<Boolean> checkPaymentStatus(@PathVariable Long userId) {
        Member member = getMemberById(userId);
        boolean isRegistered = paymentService.isPaymentRegistered(member);

        log.info("결제 상태 조회: userId={}, registered={}", userId, isRegistered);
        return ResponseEntity.ok(isRegistered);
    }

    /**
     * 결제 정보 조회
     */
    @GetMapping
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long userId) {
        Member member = getMemberById(userId);
        Payment payment = paymentService.getPaymentByMember(member)
                .orElseThrow(PaymentNotFoundException::new);

        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    /**
     * 결제 정보 삭제
     */
    @DeleteMapping
    public ResponseEntity<Void> deletePayment(@PathVariable Long userId) {
        Member member = getMemberById(userId);
        paymentService.deletePayment(member);

        log.info("결제 정보 삭제: userId={}", userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 결제 내역 조회 (Mock)
     */
    @GetMapping("/history")
    public ResponseEntity<List<PaymentHistoryResponse>> getPaymentHistory(@PathVariable Long userId) {
        Member member = getMemberById(userId);

        // Mock 데이터 반환 (DB 연결 시 실제 조회로 교체)
        List<PaymentHistoryResponse> history = createMockPaymentHistory();

        log.info("결제 내역 조회: userId={}, count={}", userId, history.size());
        return ResponseEntity.ok(history);
    }

    private List<PaymentHistoryResponse> createMockPaymentHistory() {
        return Arrays.asList(
                PaymentHistoryResponse.builder()
                        .id("INV-2024-10")
                        .date("2024-10-01")
                        .amount(4900)
                        .status("paid")
                        .invoiceUrl("#")
                        .build(),
                PaymentHistoryResponse.builder()
                        .id("INV-2024-09")
                        .date("2024-09-01")
                        .amount(4900)
                        .status("paid")
                        .invoiceUrl("#")
                        .build(),
                PaymentHistoryResponse.builder()
                        .id("INV-2024-08")
                        .date("2024-08-01")
                        .amount(4900)
                        .status("paid")
                        .invoiceUrl("#")
                        .build()
        );
    }

    /**
     * 토큰 구매
     */
    @PostMapping("/purchase-tokens")
    public ResponseEntity<TokenPurchaseResponse> purchaseTokens(
            @PathVariable Long userId,
            @RequestBody TokenPurchaseRequest request
    ) {
        Member member = getMemberById(userId);

        // 요청 검증
        request.validate();

        // 패키지 정보 조회
        TokenPackage tokenPackage = TokenPackage.fromId(request.getPackageId());

        String impUid;

        // 빌링키 사용 여부에 따라 결제 방식 선택
        if (Boolean.TRUE.equals(request.getUseBillingKey())) {
            // 빌링키로 자동 결제
            String merchantUid = "TOKEN_" + System.currentTimeMillis();
            String orderName = String.format("토큰 %d개 구매", tokenPackage.getTokenAmount());
            impUid = paymentService.payWithBillingKey(member, merchantUid, orderName, tokenPackage.getPrice());
            log.info("빌링키 결제 사용: userId={}, impUid={}", userId, impUid);
        } else {
            // 일반 결제 검증
            impUid = request.getImpUid();
            paymentService.verifyPayment(impUid);
            log.info("일반 결제 사용: userId={}, impUid={}", userId, impUid);
        }

        // Billing 정보 조회 및 토큰 추가
        Billing billing = billingService.getBillingByMember(member)
                .orElseThrow(BillingNotFoundException::new);

        billing.addTokens(tokenPackage.getTotalTokens());
        billingService.saveBilling(billing);

        TokenPurchaseResponse response = TokenPurchaseResponse.builder()
                .transactionId("TXN-" + System.currentTimeMillis())
                .purchasedTokens(tokenPackage.getTokenAmount())
                .bonusTokens(tokenPackage.getBonusTokens())
                .totalTokens(tokenPackage.getTotalTokens())
                .currentTokens(billing.getCurrentTokens())
                .purchaseDate(LocalDateTime.now().format(DateConstants.DATETIME_FORMAT))
                .build();

        log.info("토큰 구매 완료: userId={}, packageId={}, tokens={}, newTotal={}, usedBillingKey={}",
                userId, request.getPackageId(), tokenPackage.getTotalTokens(), billing.getCurrentTokens(),
                request.getUseBillingKey());

        return ResponseEntity.ok(response);
    }
}
