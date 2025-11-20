package com.budgetops.backend.ncp.service;

import com.budgetops.backend.ncp.dto.NcpAccountCreateRequest;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
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
public class NcpAccountService {
    private final NcpAccountRepository accountRepo;
    private final NcpCredentialValidator credentialValidator;

    @Value("${app.ncp.validate:true}")
    private boolean validate;

    @Transactional
    public NcpAccount createWithVerify(NcpAccountCreateRequest req) {
        // 입력값 trim
        String accessKey = req.getAccessKey() != null ? req.getAccessKey().trim() : null;
        String secretKey = req.getSecretKey() != null ? req.getSecretKey().trim() : null;
        String name = req.getName() != null ? req.getName().trim() : null;
        String regionCode = req.getRegionCode() != null ? req.getRegionCode().trim() : null;

        log.info("Creating NCP account with accessKey: {}", accessKey);

        // 기존 계정이 있는지 확인 (활성/비활성 모두 포함)
        var existingAccount = accountRepo.findByAccessKey(accessKey);

        if (existingAccount.isPresent()) {
            NcpAccount account = existingAccount.get();
            log.info("Found existing account with id: {}, active: {}", account.getId(), account.getActive());

            // 활성 계정이면 오류
            if (Boolean.TRUE.equals(account.getActive())) {
                log.warn("Attempt to register already active account with accessKey: {}", accessKey);
                throw new IllegalArgumentException("이미 등록된 accessKey 입니다.");
            }

            // 비활성 계정이면 재활성화 및 정보 업데이트
            log.info("Reactivating inactive account with id: {}", account.getId());

            if (validate) {
                boolean ok = credentialValidator.isValid(accessKey, secretKey, regionCode);
                if (!ok) {
                    log.error("Invalid NCP credentials for accessKey: {}", accessKey);
                    throw new IllegalArgumentException("NCP 자격증명이 유효하지 않습니다.");
                }
            }

            // 계정 정보 업데이트
            account.setName(name);
            account.setRegionCode(regionCode);
            account.setSecretKeyEnc(secretKey); // @Convert에 의해 암호화 저장
            account.setSecretKeyLast4(secretKey.substring(Math.max(0, secretKey.length() - 4)));
            account.setActive(Boolean.TRUE); // 재활성화

            NcpAccount saved = accountRepo.save(account);
            log.info("Successfully reactivated account with id: {}, name: {}, active: {}, regionCode: {}",
                    saved.getId(), saved.getName(), saved.getActive(), saved.getRegionCode());

            // 재활성화 후 즉시 활성 상태 확인
            NcpAccount verifyAccount = accountRepo.findById(saved.getId()).orElse(null);
            if (verifyAccount != null) {
                log.info("Verification: Account {} is now active: {}", verifyAccount.getId(), verifyAccount.getActive());
            }

            return saved;
        }

        // 새 계정 생성
        log.info("Creating new NCP account with accessKey: {}", accessKey);

        if (validate) {
            boolean ok = credentialValidator.isValid(accessKey, secretKey, regionCode);
            if (!ok) {
                log.error("Invalid NCP credentials for new account with accessKey: {}", accessKey);
                throw new IllegalArgumentException("NCP 자격증명이 유효하지 않습니다.");
            }
        }

        NcpAccount a = new NcpAccount();
        a.setName(name);
        a.setRegionCode(regionCode);
        a.setAccessKey(accessKey);
        a.setSecretKeyEnc(secretKey); // @Convert에 의해 암호화 저장
        a.setSecretKeyLast4(secretKey.substring(Math.max(0, secretKey.length() - 4)));
        a.setActive(Boolean.TRUE); // 등록 즉시 활성

        NcpAccount saved = accountRepo.save(a);
        log.info("Successfully created new account with id: {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NcpAccount> getActiveAccounts() {
        return accountRepo.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public NcpAccount getAccountInfo(Long accountId) {
        return accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다."));
    }

    @Transactional
    public void deactivateAccount(Long accountId) {
        log.info("Deactivating NCP account with id: {}", accountId);

        NcpAccount account = accountRepo.findById(accountId)
                .orElseThrow(() -> {
                    log.error("Account not found with id: {}", accountId);
                    return new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다.");
                });

        if (Boolean.TRUE.equals(account.getActive())) {
            account.setActive(Boolean.FALSE);
            accountRepo.save(account);
            log.info("Successfully deactivated account with id: {}, accessKey: {}",
                    accountId, account.getAccessKey());
        } else {
            log.warn("Account with id: {} is already inactive", accountId);
        }
    }
}
