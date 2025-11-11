package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
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
public class AwsAccountService {
    private final AwsAccountRepository accountRepo;
    private final AwsCredentialValidator credentialValidator;

    @Value("${app.aws.validate:true}")
    private boolean validate;

    @Transactional
    public AwsAccount createWithVerify(AwsAccountCreateRequest req) {
        log.info("Creating AWS account with accessKeyId: {}", req.getAccessKeyId());
        
        // 기존 계정이 있는지 확인 (활성/비활성 모두 포함)
        var existingAccount = accountRepo.findByAccessKeyId(req.getAccessKeyId());
        
        if (existingAccount.isPresent()) {
            AwsAccount account = existingAccount.get();
            log.info("Found existing account with id: {}, active: {}", account.getId(), account.getActive());
            
            // 활성 계정이면 오류
            if (Boolean.TRUE.equals(account.getActive())) {
                log.warn("Attempt to register already active account with accessKeyId: {}", req.getAccessKeyId());
                throw new IllegalArgumentException("이미 등록된 accessKeyId 입니다.");
            }
            
            // 비활성 계정이면 재활성화 및 정보 업데이트
            log.info("Reactivating inactive account with id: {}", account.getId());
            
            if (validate) {
                boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
                if (!ok) {
                    log.error("Invalid AWS credentials for accessKeyId: {}", req.getAccessKeyId());
                    throw new IllegalArgumentException("AWS 자격증명이 유효하지 않습니다.");
                }
            }
            
            // 계정 정보 업데이트
            account.setName(req.getName());
            account.setDefaultRegion(req.getDefaultRegion());
            account.setSecretKeyEnc(req.getSecretAccessKey()); // @Convert에 의해 암호화 저장
            String sk = req.getSecretAccessKey();
            account.setSecretKeyLast4(sk.substring(Math.max(0, sk.length() - 4)));
            account.setActive(Boolean.TRUE); // 재활성화
            
            AwsAccount saved = accountRepo.save(account);
            log.info("Successfully reactivated account with id: {}", saved.getId());
            return saved;
        }

        // 새 계정 생성
        log.info("Creating new AWS account with accessKeyId: {}", req.getAccessKeyId());
        
        if (validate) {
            boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
            if (!ok) {
                log.error("Invalid AWS credentials for new account with accessKeyId: {}", req.getAccessKeyId());
                throw new IllegalArgumentException("AWS 자격증명이 유효하지 않습니다.");
            }
        }

        AwsAccount a = new AwsAccount();
        a.setName(req.getName());
        a.setDefaultRegion(req.getDefaultRegion());
        a.setAccessKeyId(req.getAccessKeyId());
        a.setSecretKeyEnc(req.getSecretAccessKey()); // @Convert에 의해 암호화 저장
        String sk = req.getSecretAccessKey();
        a.setSecretKeyLast4(sk.substring(Math.max(0, sk.length() - 4)));
        a.setActive(Boolean.TRUE); // 등록 즉시 활성
        
        AwsAccount saved = accountRepo.save(a);
        log.info("Successfully created new account with id: {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AwsAccount> getActiveAccounts() {
        return accountRepo.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public AwsAccount getAccountInfo(Long accountId) {
        return accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다."));
    }

    @Transactional
    public void deactivateAccount(Long accountId) {
        log.info("Deactivating AWS account with id: {}", accountId);
        
        AwsAccount account = accountRepo.findById(accountId)
                .orElseThrow(() -> {
                    log.error("Account not found with id: {}", accountId);
                    return new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다.");
                });
        
        if (Boolean.TRUE.equals(account.getActive())) {
            account.setActive(Boolean.FALSE);
            accountRepo.save(account);
            log.info("Successfully deactivated account with id: {}, accessKeyId: {}", 
                    accountId, account.getAccessKeyId());
        } else {
            log.warn("Account with id: {} is already inactive", accountId);
        }
    }
}


