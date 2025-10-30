package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AwsAccountService {
    private final AwsAccountRepository accountRepo;

    @Transactional
    public AwsAccount createWithoutVerify(AwsAccountCreateRequest req) {
        accountRepo.findByAccessKeyId(req.getAccessKeyId()).ifPresent(a -> {
            throw new IllegalArgumentException("이미 등록된 accessKeyId 입니다.");
        });

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
}


