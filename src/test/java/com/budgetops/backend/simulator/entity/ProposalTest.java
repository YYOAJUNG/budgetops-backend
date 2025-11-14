package com.budgetops.backend.simulator.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ProposalTest {
    @ParameterizedTest
    @MethodSource("rejectProposalProvider")
    void rejectProposalInvalidTest(Proposal.ProposalStatus givenStatus) {
        Proposal proposal = new Proposal();
        proposal.setStatus(givenStatus);

        assertThrows(IllegalStateException.class, proposal::rejectProposal);
    }

    @Test
    void rejectProposalTest() {
        final var proposal = new Proposal();
        proposal.setStatus(Proposal.ProposalStatus.PENDING);

        final var actual = proposal.rejectProposal();
        assertThat(actual.getStatus()).isEqualTo(Proposal.ProposalStatus.REJECTED);
    }

    private static Stream<Arguments> rejectProposalProvider() {
        return Arrays.stream(Proposal.ProposalStatus.values())
                .filter(value -> !value.equals(Proposal.ProposalStatus.PENDING))
                .map(Arguments::of);
    }
}