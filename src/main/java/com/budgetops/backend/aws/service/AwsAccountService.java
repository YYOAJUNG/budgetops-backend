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
    private final WorkspaceRepository workspaceRepo;
    private final MemberRepository memberRepo;

    @Value("${app.aws.validate:true}")
    private boolean validate;

    @Transactional
    public AwsAccount createWithVerify(AwsAccountCreateRequest req, Long memberId) {
        log.info("Creating AWS account with accessKeyId: {}", req.getAccessKeyId());

        Member member = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member를 찾을 수 없습니다: " + memberId));

        Workspace workspace = getOrCreateWorkspace(member);

        var existingAccount = accountRepo.findByAccessKeyId(req.getAccessKeyId());

        if (existingAccount.isPresent()) {
            AwsAccount account = existingAccount.get();
            log.info("Found existing account with id: {}, active: {}", account.getId(), account.getActive());

            if (Boolean.TRUE.equals(account.getActive())) {
                log.warn("Attempt to register already active account with accessKeyId: {}", req.getAccessKeyId());
                throw new IllegalArgumentException("이미 등록된 accessKeyId 입니다.");
            }

            log.info("Reactivating inactive account with id: {}", account.getId());

            if (validate) {
                boolean ok = credentialValidator.isValid(req.getAccessKeyId(), req.getSecretAccessKey(), req.getDefaultRegion());
                if (!ok) {
                    log.error("Invalid AWS credentials for accessKeyId: {}", req.getAccessKeyId());
                    throw new IllegalArgumentException("AWS 자격증명이 유효하지 않습니다.");
                }
            }

            account.setWorkspace(workspace);

            // 계정 정보 업데이트
            account.setName(req.getName());
            account.setDefaultRegion(req.getDefaultRegion());
            account.setSecretKeyEnc(req.getSecretAccessKey()); // @Convert에 의해 암호화 저장
            String sk = req.getSecretAccessKey();
            account.setSecretKeyLast4(sk.substring(Math.max(0, sk.length() - 4)));
            account.setActive(Boolean.TRUE); // 재활성화

            AwsAccount saved = accountRepo.save(account);
            log.info("Successfully reactivated account with id: {}, name: {}, active: {}, defaultRegion: {}",
                    saved.getId(), saved.getName(), saved.getActive(), saved.getDefaultRegion());

            AwsAccount verifyAccount = accountRepo.findById(saved.getId()).orElse(null);
            if (verifyAccount != null) {
                log.info("Verification: Account {} is now active: {}", verifyAccount.getId(), verifyAccount.getActive());
            }

            return saved;
        }

        log.info("Creating new AWS account with accessKeyId: {}", req.getAccessKeyId());

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
        a.setSecretKeyEnc(req.getSecretAccessKey()); // @Convert에 의해 암호화 저장
        String sk = req.getSecretAccessKey();
        a.setSecretKeyLast4(sk.substring(Math.max(0, sk.length() - 4)));
        a.setActive(Boolean.TRUE); // 등록 즉시 활성

        AwsAccount saved = accountRepo.save(a);
        log.info("Successfully created new account with id: {}", saved.getId());
        return saved;
    }

    /**
     * Member의 Workspace를 조회하거나, 없으면 자동 생성
     */
    private Workspace getOrCreateWorkspace(Member member) {
        // Member가 owner인 Workspace 조회
        List<Workspace> workspaces = workspaceRepo.findByOwner(member);

        if (!workspaces.isEmpty()) {
            // 첫 번째 Workspace 반환
            return workspaces.get(0);
        }

        // Workspace가 없으면 자동 생성
        Workspace workspace = Workspace.builder()
                .name(member.getName() + "'s Workspace")
                .description("Default workspace for " + member.getEmail())
                .owner(member)
                .build();

        workspace.addMember(member);

        return workspaceRepo.save(workspace);
    }

    @Transactional(readOnly = true)
    public List<AwsAccount> getActiveAccounts() {
        return accountRepo.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<AwsAccount> getActiveAccountsByWorkspace(Long workspaceId) {
        return accountRepo.findByWorkspaceIdAndActiveTrue(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<AwsAccount> getActiveAccountsByMember(Long memberId) {
        // Member 조회
        Member member = memberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member를 찾을 수 없습니다: " + memberId));

        // Member의 Workspace 조회
        List<Workspace> workspaces = workspaceRepo.findByOwner(member);

        if (workspaces.isEmpty()) {
            return List.of(); // Workspace가 없으면 빈 리스트 반환
        }

        // 첫 번째 Workspace의 활성 AWS 계정 반환
        return accountRepo.findByWorkspaceIdAndActiveTrue(workspaces.get(0).getId());
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


