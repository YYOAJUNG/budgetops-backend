package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.aws.repository.AwsResourceRepository;
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
public class AwsAccountService {
    private final AwsAccountRepository accountRepo;
    private final AwsCredentialValidator credentialValidator;
    private final AwsResourceRepository resourceRepository;
    private final MemberRepository memberRepository;

    @Value("${app.aws.validate:true}")
    private boolean validate;

    @Transactional
    public AwsAccount createWithVerify(AwsAccountCreateRequest req, Long memberId) {
        log.info("Creating AWS account with accessKeyId: {}", req.getAccessKeyId());

        Member member = getMemberOrThrow(memberId);
        var existingAccount = accountRepo.findByAccessKeyId(req.getAccessKeyId());

        if (existingAccount.isPresent()) {
            AwsAccount account = existingAccount.get();
            log.info("Found existing account with id: {}, active: {}", account.getId(), account.getActive());

            if (validate) {
                boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
                if (!ok) {
                    log.error("Invalid AWS credentials for accessKeyId: {}", req.getAccessKeyId());
                    throw new IllegalArgumentException("AWS 자격증명이 유효하지 않습니다.");
                }
            }

            Long previousOwnerId = Optional.ofNullable(account.getOwner())
                    .map(Member::getId)
                    .orElse(null);
            if (previousOwnerId != null && !previousOwnerId.equals(memberId)) {
                log.info("Reassigning AWS account {} from member {} to {}", account.getId(), previousOwnerId, memberId);
            }

            account.setOwner(member);
            account.setName(req.getName());
            account.setDefaultRegion(req.getDefaultRegion());
            account.setSecretKeyEnc(req.getSecretAccessKey());
            String sk = req.getSecretAccessKey();
            account.setSecretKeyLast4(sk.substring(Math.max(0, sk.length() - 4)));
            account.setActive(Boolean.TRUE);

            AwsAccount saved = accountRepo.save(account);
            log.info("Successfully reactivated account with id: {}, name: {}, active: {}, defaultRegion: {}",
                    saved.getId(), saved.getName(), saved.getActive(), saved.getDefaultRegion());

            return saved;
        }

        if (validate) {
            boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
            if (!ok) {
                log.error("Invalid AWS credentials for new account with accessKeyId: {}", req.getAccessKeyId());
                throw new IllegalArgumentException("AWS 자격증명이 유효하지 않습니다.");
            }
        }

        AwsAccount a = new AwsAccount();
        a.setOwner(member);
        a.setName(req.getName());
        a.setDefaultRegion(req.getDefaultRegion());
        a.setAccessKeyId(req.getAccessKeyId());
        a.setSecretKeyEnc(req.getSecretAccessKey());
        String sk = req.getSecretAccessKey();
        a.setSecretKeyLast4(sk.substring(Math.max(0, sk.length() - 4)));
        a.setActive(Boolean.TRUE);

        AwsAccount saved = accountRepo.save(a);
        log.info("Successfully created new account with id: {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AwsAccount> getActiveAccounts(Long memberId) {
        getMemberOrThrow(memberId);
        return accountRepo.findByOwnerIdAndActiveTrue(memberId);
    }

    @Transactional(readOnly = true)
    public AwsAccount getAccountInfo(Long accountId, Long memberId) {
        return accountRepo.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다."));
    }

    @Transactional
    public void deactivateAccount(Long accountId, Long memberId) {
        log.info("Deactivating AWS account with id: {}", accountId);

        AwsAccount account = accountRepo.findByIdAndOwnerId(accountId, memberId)
                .orElseThrow(() -> {
                    log.error("Account not found with id: {}", accountId);
                    return new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다.");
                });

        var resources = resourceRepository.findByAwsAccountId(accountId);
        if (!resources.isEmpty()) {
            resourceRepository.deleteAll(resources);
            log.info("Deleted {} AWS resources linked to account {}", resources.size(), accountId);
        }

        accountRepo.delete(account);
        log.info("Successfully deleted AWS account with id: {}, accessKeyId: {}", accountId, account.getAccessKeyId());
    }

    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member를 찾을 수 없습니다: " + memberId));
    }
}


