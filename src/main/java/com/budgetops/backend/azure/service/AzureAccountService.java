package com.budgetops.backend.azure.service;

import com.budgetops.backend.azure.dto.AzureAccountCreateRequest;
import com.budgetops.backend.azure.entity.AzureAccount;
import com.budgetops.backend.azure.repository.AzureAccountRepository;
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
public class AzureAccountService {

    private final AzureAccountRepository accountRepository;
    private final AzureCredentialValidator credentialValidator;
    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;

    @Value("${app.azure.validate:true}")
    private boolean validate;

    @Transactional
    public AzureAccount createWithVerify(AzureAccountCreateRequest request, Long memberId) {
        log.info("Creating Azure account with clientId={}, subscriptionId={}", request.getClientId(), request.getSubscriptionId());

        Member member = getMemberOrThrow(memberId);
        Workspace workspace = getOrCreateWorkspace(member);

        var existing = accountRepository.findByClientIdAndSubscriptionId(request.getClientId(), request.getSubscriptionId());

        if (existing.isPresent()) {
            AzureAccount account = existing.get();
            boolean hasOwner = account.getWorkspace() != null && account.getWorkspace().getOwner() != null;
            boolean sameOwner = hasOwner && account.getWorkspace().getOwner().getId().equals(memberId);

            if (hasOwner && !sameOwner) {
                throw new IllegalArgumentException("다른 사용자가 등록한 Azure 계정입니다.");
            }

            if (validate && !credentialValidator.isValid(request.getTenantId(), request.getClientId(),
                    request.getClientSecret(), request.getSubscriptionId())) {
                throw new IllegalArgumentException("Azure 자격증명이 유효하지 않습니다.");
            }

            account.setWorkspace(workspace);
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
        account.setWorkspace(workspace);
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
    public List<AzureAccount> getActiveAccounts(Long memberId) {
        Member member = getMemberOrThrow(memberId);
        Workspace workspace = findPrimaryWorkspace(member);
        if (workspace == null) {
            return List.of();
        }
        return accountRepository.findByWorkspaceIdAndActiveTrue(workspace.getId());
    }

    @Transactional(readOnly = true)
    public AzureAccount getAccountInfo(Long accountId, Long memberId) {
        return accountRepository.findByIdAndWorkspaceOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Azure 계정을 찾을 수 없습니다."));
    }

    @Transactional
    public void deactivateAccount(Long accountId, Long memberId) {
        AzureAccount account = accountRepository.findByIdAndWorkspaceOwnerId(accountId, memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Azure 계정을 찾을 수 없습니다."));
        if (Boolean.TRUE.equals(account.getActive())) {
            account.setActive(Boolean.FALSE);
            accountRepository.save(account);
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

