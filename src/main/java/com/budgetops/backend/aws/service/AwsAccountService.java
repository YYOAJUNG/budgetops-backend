package com.budgetops.backend.aws.service;

import com.budgetops.backend.aws.dto.AwsAccountCreateRequest;
import com.budgetops.backend.aws.entity.AwsAccount;
import com.budgetops.backend.aws.repository.AwsAccountRepository;
import com.budgetops.backend.billing.entity.Workspace;
import com.budgetops.backend.billing.repository.WorkspaceRepository;
import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.domain.user.repository.MemberRepository;
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
    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;

    @Value("${app.aws.validate:true}")
    private boolean validate;

    @Transactional
    public AwsAccount createWithVerify(AwsAccountCreateRequest req, Long memberId) {
        log.info("Creating AWS account with accessKeyId: {}", req.getAccessKeyId());

        Member member = getMemberOrThrow(memberId);
        Workspace workspace = getOrCreateWorkspace(member);

        var existingAccount = accountRepo.findByAccessKeyId(req.getAccessKeyId());

        if (existingAccount.isPresent()) {
            AwsAccount account = existingAccount.get();
            log.info("Found existing account with id: {}, active: {}", account.getId(), account.getActive());

            boolean hasOwner = account.getWorkspace() != null && account.getWorkspace().getOwner() != null;
            boolean sameOwner = hasOwner && account.getWorkspace().getOwner().getId().equals(memberId);
            if (hasOwner && !sameOwner) {
                log.warn("Attempt to register AWS account owned by another member. memberId={}, ownerId={}",
                        memberId, account.getWorkspace().getOwner().getId());
                throw new IllegalArgumentException("다른 사용자가 등록한 AWS 계정입니다.");
            }

            if (validate) {
                boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
                if (!ok) {
                    log.error("Invalid AWS credentials for accessKeyId: {}", req.getAccessKeyId());
                    throw new IllegalArgumentException("AWS 자격증명이 유효하지 않습니다.");
                }
            }

            account.setWorkspace(workspace);
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
        a.setWorkspace(workspace);
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
        Member member = getMemberOrThrow(memberId);
        Workspace workspace = findPrimaryWorkspace(member);
        if (workspace == null) {
            return List.of();
        }
        return accountRepo.findByWorkspaceIdAndActiveTrue(workspace.getId());
    }

    @Transactional(readOnly = true)
    public AwsAccount getAccountInfo(Long accountId, Long memberId) {
        return accountRepo.findByIdAndWorkspaceOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "계정을 찾을 수 없습니다."));
    }

    @Transactional
    public void deactivateAccount(Long accountId, Long memberId) {
        log.info("Deactivating AWS account with id: {}", accountId);

        AwsAccount account = accountRepo.findByIdAndWorkspaceOwnerId(accountId, memberId)
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

    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member를 찾을 수 없습니다: " + memberId));
    }

    private Workspace findPrimaryWorkspace(Member member) {
        List<Workspace> workspaces = workspaceRepository.findByOwner(member);
        if (workspaces.isEmpty()) {
            return null;
        }
        return workspaces.get(0);
    }

    private Workspace getOrCreateWorkspace(Member member) {
        Workspace existing = findPrimaryWorkspace(member);
        if (existing != null) {
            existing.addMember(member);
            return existing;
        }

        Workspace workspace = Workspace.builder()
                .name(member.getName() + "'s Workspace")
                .description("Default workspace for " + member.getEmail())
                .owner(member)
                .build();

        workspace.addMember(member);
        return workspaceRepository.save(workspace);
    }
}


