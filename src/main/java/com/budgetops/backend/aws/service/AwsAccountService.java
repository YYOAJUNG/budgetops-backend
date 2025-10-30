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
        accountRepo.findByAccessKeyId(req.getAccessKeyId()).ifPresent(a -> {
            throw new IllegalArgumentException("이미 등록된 accessKeyId 입니다.");
        });

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
}


