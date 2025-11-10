package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class AwsAccountService {
    private final AwsAccountRepository accountRepo;
    private final AwsCredentialValidator credentialValidator;

    @Value("${app.aws.validate:true}")
    private boolean validate;

    @Transactional
    public AwsAccount createWithVerify(AwsAccountCreateRequest req) {
        // 기존 계정이 있는지 확인
        var existingAccount = accountRepo.findByAccessKeyId(req.getAccessKeyId());
        
        if (existingAccount.isPresent()) {
            AwsAccount account = existingAccount.get();
            // 활성 계정이면 오류
            if (Boolean.TRUE.equals(account.getActive())) {
                throw new IllegalArgumentException("이미 등록된 accessKeyId 입니다.");
            }
            // 비활성 계정이면 재활성화 및 정보 업데이트
            if (validate) {
                boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
                if (!ok) {
                    throw new IllegalArgumentException("AWS 자격증명이 유효하지 않습니다.");
                }
            }
            account.setName(req.getName());
            account.setDefaultRegion(req.getDefaultRegion());
            account.setSecretKeyEnc(req.getSecretAccessKey()); // @Convert에 의해 암호화 저장
            String sk = req.getSecretAccessKey();
            account.setSecretKeyLast4(sk.substring(Math.max(0, sk.length() - 4)));
            account.setActive(Boolean.TRUE); // 재활성화
            return accountRepo.save(account);
        }

        // 새 계정 생성
        if (validate) {
            boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
            if (!ok) {
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
        return accountRepo.save(a);
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
        AwsAccount account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(account.getActive())) {
            account.setActive(Boolean.FALSE);
            accountRepo.save(account);
        }
    }
}


