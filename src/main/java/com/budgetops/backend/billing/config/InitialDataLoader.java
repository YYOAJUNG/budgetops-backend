package com.budgetops.backend.billing.config;

import com.budgetops.backend.billing.entity.Billing;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.billing.enums.BillingPlan;
import com.budgetops.backend.billing.repository.BillingRepository;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 테스트 데이터 자동 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InitialDataLoader implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final BillingRepository billingRepository;

    @Override
    public void run(String... args) {
        // 테스트용 Member가 없으면 생성
        if (memberRepository.findById(1L).isEmpty()) {
            Member testMember = Member.builder()
                    .email("test@budgetops.com")
                    .name("테스트 사용자")
                    .build();
            memberRepository.save(testMember);

            // 기본 FREE 플랜 Billing 생성
            Billing billing = Billing.builder()
                    .member(testMember)
                    .currentPlan(BillingPlan.FREE)
                    .currentPrice(0)
                    .build();
            billingRepository.save(billing);

            log.info("테스트 사용자 생성 완료: userId=1, email=test@budgetops.com");
        }
    }
}
