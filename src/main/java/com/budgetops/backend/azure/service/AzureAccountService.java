package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.dto.AzureAccountCreateRequest;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureAccountService {

    private final AzureAccountRepository accountRepository;
    private final AzureCredentialValidator credentialValidator;
    private final MemberRepository memberRepository;

    @Value("${app.azure.validate:true}")
    private boolean validate;

    @Transactional
    public AzureAccount createWithVerify(AzureAccountCreateRequest request, Long memberId) {
        log.info("Creating Azure account with clientId={}, subscriptionId={}", request.getClientId(), request.getSubscriptionId());

        Member member = getMemberOrThrow(memberId);

        var existing = accountRepository.findByClientIdAndSubscriptionId(request.getClientId(), request.getSubscriptionId());

        if (existing.isPresent()) {
            AzureAccount account = existing.get();

            if (Boolean.TRUE.equals(account.getActive())) {
                throw new IllegalArgumentException("이미 등록된 Azure 계정입니다.");
            }

            if (validate && !credentialValidator.isValid(request.getTenantId(), request.getClientId(),
                    request.getClientSecret(), request.getSubscriptionId())) {
                throw new IllegalArgumentException("Azure 자격증명이 유효하지 않습니다.");
            }

            Long previousOwnerId = Optional.ofNullable(account.getOwner())
                    .map(Member::getId)
                    .orElse(null);
            if (previousOwnerId != null && !previousOwnerId.equals(memberId)) {
                log.info("Reassigning Azure account {} from member {} to {}", account.getId(), previousOwnerId, memberId);
            }

            account.setOwner(member);
            account.setName(request.getName());
            account.setSubscriptionId(request.getSubscriptionId());
            account.setTenantId(request.getTenantId());
            account.setClientId(request.getClientId());
            account.setClientSecretEnc(request.getClientSecret());
            String secret = request.getClientSecret();
            account.setClientSecretLast4(secret.substring(Math.max(0, secret.length() - 4)));
            account.setActive(Boolean.TRUE);

            return accountRepository.save(account);
        }

        if (validate && !credentialValidator.isValid(request.getTenantId(), request.getClientId(),
                request.getClientSecret(), request.getSubscriptionId())) {
            throw new IllegalArgumentException("Azure 자격증명이 유효하지 않습니다.");
        }

        AzureAccount account = new AzureAccount();
        account.setOwner(member);
        account.setName(request.getName());
        account.setSubscriptionId(request.getSubscriptionId());
        account.setTenantId(request.getTenantId());
        account.setClientId(request.getClientId());
        account.setClientSecretEnc(request.getClientSecret());
        String secret = request.getClientSecret();
        account.setClientSecretLast4(secret.substring(Math.max(0, secret.length() - 4)));
        account.setActive(Boolean.TRUE);
        // 크레딧 기본 설정은 엔티티의 기본값(hasCredit=true)과 null 한도를 사용하되,
        // 요청에 값이 있는 경우 우선 적용
        if (request.getHasCredit() != null) {
            account.setHasCredit(request.getHasCredit());
        }
        if (request.getCreditLimitAmount() != null) {
            account.setCreditLimitAmount(request.getCreditLimitAmount());
        }
        if (request.getCreditStartDate() != null) {
            account.setCreditStartDate(request.getCreditStartDate());
        }
        if (request.getCreditEndDate() != null) {
            account.setCreditEndDate(request.getCreditEndDate());
        }

        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<AzureAccount> getActiveAccounts(Long memberId) {
        getMemberOrThrow(memberId);
        return accountRepository.findByOwnerIdAndActiveTrue(memberId);
    }

    @Transactional(readOnly = true)
    public AzureAccount getAccountInfo(Long accountId, Long memberId) {
        return accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Azure 계정을 찾을 수 없습니다."));
    }

    @Transactional
    public void deactivateAccount(Long accountId, Long memberId) {
        AzureAccount account = accountRepository.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Azure 계정을 찾을 수 없습니다."));
        accountRepository.delete(account);
        log.info("Deleted Azure account id={} for member {}", accountId, memberId);
    }

    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member를 찾을 수 없습니다: " + memberId));
    }
}

