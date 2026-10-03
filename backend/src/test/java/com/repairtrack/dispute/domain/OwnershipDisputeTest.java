package com.repairtrack.dispute.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OwnershipDisputeTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final String STATEMENT = "Ik heb deze auto gekocht, zie het contract.";

    private final OwnershipDispute dispute = OwnershipDispute.open(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), STATEMENT, Duration.ofDays(14), NOW);

    @Test
    void theOwnerCanRespondOnceBeforeTheDeadline() {
        assertThat(dispute.canRespond(NOW)).isTrue();
        assertThat(dispute.isReviewable(NOW)).isFalse();

        dispute.respond(STATEMENT, NOW.plusSeconds(60));

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.AWAITING_REVIEW);
        assertThat(dispute.isReviewable(NOW)).isTrue();
        assertThatThrownBy(() -> dispute.respond(STATEMENT, NOW)).isInstanceOf(DisputeClosedException.class);
    }

    @Test
    void afterTheDeadlineItIsReviewableWithoutAResponse() {
        Instant later = NOW.plus(Duration.ofDays(14));

        assertThat(dispute.canRespond(later)).isFalse();
        assertThat(dispute.isReviewable(later)).isTrue();
        assertThatThrownBy(() -> dispute.respond(STATEMENT, later)).isInstanceOf(DisputeClosedException.class);
    }

    @Test
    void aDecisionNeedsAReviewableDisputeAndIsFinal() {
        UUID admin = UUID.randomUUID();
        assertThatThrownBy(() -> dispute.reject(admin, STATEMENT, NOW))
                .isInstanceOf(DisputeNotReviewableException.class);

        Instant later = NOW.plus(Duration.ofDays(15));
        dispute.uphold(admin, STATEMENT, LocalDate.of(2025, 1, 1), later);

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.UPHELD);
        assertThat(dispute.getNewOwnerSince()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThatThrownBy(() -> dispute.reject(admin, STATEMENT, later)).isInstanceOf(DisputeClosedException.class);
    }

    @Test
    void statementsMustHaveSubstance() {
        assertThatThrownBy(() -> OwnershipDispute.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "  kort ", Duration.ofDays(14), NOW))
                .isInstanceOf(InvalidDisputeException.class);
        assertThatThrownBy(() -> dispute.respond("x".repeat(2001), NOW)).isInstanceOf(InvalidDisputeException.class);
    }

    @Test
    void ownersCannotDisputeThemselves() {
        UUID same = UUID.randomUUID();
        assertThatThrownBy(() -> OwnershipDispute.open(UUID.randomUUID(), same, same, UUID.randomUUID(), STATEMENT,
                Duration.ofDays(14), NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
