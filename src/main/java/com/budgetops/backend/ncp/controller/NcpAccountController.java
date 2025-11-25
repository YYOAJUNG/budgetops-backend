package com.budgetops.backend.ncp.controller;

import com.budgetops.backend.ncp.dto.NcpAccountCreateRequest;
import com.budgetops.backend.ncp.dto.NcpAccountResponse;
import com.budgetops.backend.ncp.dto.NcpDailyUsage;
import com.budgetops.backend.ncp.dto.NcpMonthlyCost;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.service.NcpAccountService;
import com.budgetops.backend.ncp.service.NcpCostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/ncp/accounts")
@RequiredArgsConstructor
public class NcpAccountController {
    private final NcpAccountService service;
    private final NcpCostService costService;

    /**
     * NCP 계정 등록 (자격증명 검증 후 저장)
     */
    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody NcpAccountCreateRequest req) {
        NcpAccount saved = service.createWithVerify(req, getCurrentMemberId());
        // secret은 응답에서 제외: 최소 정보만 반환
        return ResponseEntity.ok(new Object() {
            public final Long id = saved.getId();
            public final String name = saved.getName();
            public final String regionCode = saved.getRegionCode();
            public final String accessKey = saved.getAccessKey();
            public final String secretKeyLast4 = "****" + saved.getSecretKeyLast4();
            public final boolean active = Boolean.TRUE.equals(saved.getActive());
        });
    }

    /**
     * 활성 계정 목록 조회
     */
    @GetMapping
    public ResponseEntity<?> getActiveAccounts() {
        return ResponseEntity.ok(service.getActiveAccounts(getCurrentMemberId()).stream()
                .map(this::toResp)
                .toList());
    }

    /**
     * 계정 정보 조회 (id 기준)
     */
    @GetMapping("/{accountId}/info")
    public ResponseEntity<NcpAccountResponse> getAccountInfo(@PathVariable Long accountId) {
        NcpAccount a = service.getAccountInfo(accountId, getCurrentMemberId());
        return ResponseEntity.ok(toResp(a));
    }

    /**
     * 계정 비활성화 (삭제 대용)
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
        service.deactivateAccount(accountId, getCurrentMemberId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 특정 계정의 월별 비용 조회
     */
    @GetMapping("/{accountId}/costs")
    public ResponseEntity<List<NcpMonthlyCost>> getAccountCosts(
            @PathVariable Long accountId,
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth
    ) {
        // 기본값: 최근 3개월
        if (startMonth == null) {
            startMonth = LocalDate.now().minusMonths(2).format(DateTimeFormatter.ofPattern("yyyyMM"));
        }
        if (endMonth == null) {
            endMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        }

        List<NcpMonthlyCost> costs = costService.getCosts(accountId, getCurrentMemberId(), startMonth, endMonth);
        return ResponseEntity.ok(costs);
    }

    /**
     * 특정 계정의 일별 사용량 조회
     */
    @GetMapping("/{accountId}/usage/daily")
    public ResponseEntity<List<NcpDailyUsage>> getAccountDailyUsage(
            @PathVariable Long accountId,
            @RequestParam(required = false) String startDay,
            @RequestParam(required = false) String endDay
    ) {
        // 기본값: 이번 달 1일 ~ 오늘
        if (startDay == null) {
            startDay = LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        if (endDay == null) {
            endDay = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        List<NcpDailyUsage> usageList = costService.getDailyUsage(accountId, getCurrentMemberId(), startDay, endDay);
        return ResponseEntity.ok(usageList);
    }

    /**
     * Entity를 Response DTO로 변환
     */
    private NcpAccountResponse toResp(NcpAccount a) {
        return NcpAccountResponse.builder()
                .id(a.getId())
                .name(a.getName())
                .regionCode(a.getRegionCode())
                .accessKey(a.getAccessKey())
                .secretKeyLast4("****" + a.getSecretKeyLast4())
                .active(Boolean.TRUE.equals(a.getActive()))
                .build();
    }

    private Long getCurrentMemberId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
