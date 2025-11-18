package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.dto.AzureAccountCreateRequest;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureAccountService {

    private final AzureAccountRepository accountRepository;
    private final AzureCredentialValidator credentialValidator;

    @Value("${app.azure.validate:true}")
    private boolean validate;

    @Transactional
    public AzureAccount createWithVerify(AzureAccountCreateRequest request) {
        log.info("Creating Azure account with clientId={}, subscriptionId={}", request.getClientId(), request.getSubscriptionId());

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

    @Transactional(readOnly = true)
    public List<AzureAccount> getActiveAccounts() {
        return accountRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public AzureAccount getAccountInfo(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Azure 계정을 찾을 수 없습니다."));
    }

    @Transactional
    public void deactivateAccount(Long accountId) {
        AzureAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Azure 계정을 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(account.getActive())) {
            account.setActive(Boolean.FALSE);
            accountRepository.save(account);
        }
    }
}

