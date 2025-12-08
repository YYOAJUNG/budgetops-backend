package com.budgetops.backend.ncp.service;

import com.budgetops.backend.domain.user.entity.Member;
import com.budgetops.backend.ncp.entity.NcpAccount;
import com.budgetops.backend.ncp.repository.NcpAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("NcpServer Service 테스트")
class NcpServerServiceTest {

    @Mock
    private NcpAccountRepository accountRepository;

    @Mock
    private com.budgetops.backend.ncp.client.NcpApiClient apiClient;

    @Mock
    private NcpMetricService metricService;

    @InjectMocks
    private NcpServerService ncpServerService;

    private Member testMember;
    private NcpAccount testAccount;

    @BeforeEach
    void setUp() {
        testMember = new Member();
        testMember.setId(1L);

        testAccount = new NcpAccount();
        testAccount.setId(100L);
        testAccount.setOwner(testMember);
        testAccount.setName("Test NCP Account");
        testAccount.setAccessKey("NCPACCESSKEY");
        testAccount.setSecretKeyEnc("ncpSecretKey");
        testAccount.setRegionCode("KR");
        testAccount.setActive(Boolean.TRUE);
    }

    @Test
    @DisplayName("listInstances - 존재하지 않는 계정")
    void listInstances_AccountNotFound() {
        // given
        given(accountRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ncpServerService.listInstances(999L, "KR"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NCP 계정을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("listInstances - 비활성화된 계정")
    void listInstances_InactiveAccount() {
        // given
        testAccount.setActive(Boolean.FALSE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(testAccount));

        // when & then
        assertThatThrownBy(() -> ncpServerService.listInstances(100L, "KR"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비활성화된 계정입니다");
    }
}

