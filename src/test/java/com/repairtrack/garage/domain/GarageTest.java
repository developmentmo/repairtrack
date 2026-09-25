package com.repairtrack.garage.domain;

import static com.repairtrack.garage.GarageVerificationStatus.PENDING;
import static com.repairtrack.garage.GarageVerificationStatus.SUSPENDED;
import static com.repairtrack.garage.GarageVerificationStatus.UNVERIFIED;
import static com.repairtrack.garage.GarageVerificationStatus.VERIFIED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.repairtrack.garage.GarageVerificationStatus;

class GarageTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final UUID FOUNDER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    @Test
    void newGarageIsPendingWithNormalizedData() {
        Garage garage = newGarage();

        assertThat(garage.getVerificationStatus()).isEqualTo(PENDING);
        assertThat(garage.getPostalCode()).isEqualTo("1234 AB");
        assertThat(garage.getEmail()).isEqualTo("info@garage-test.nl");
        assertThat(garage.getVerificationChangedBy()).isEqualTo(FOUNDER);
        assertThat(garage.getCreatedBy()).isEqualTo(FOUNDER);
        assertThat(garage.isSuspended()).isFalse();
    }

    @Test
    void invalidKvkNumberIsRejected() {
        assertThatThrownBy(() -> Garage.register("G", "1234", "Street 1", "1234AB", "Utrecht", null, null,
                FOUNDER, NOW)).isInstanceOf(InvalidGarageDataException.class);
    }

    @Test
    void invalidPostalCodeIsRejected() {
        assertThatThrownBy(() -> Garage.register("G", "12345678", "Street 1", "0123AB", "Utrecht", null, null,
                FOUNDER, NOW)).isInstanceOf(InvalidGarageDataException.class);
    }

    @ParameterizedTest(name = "{0} -> {1} allowed")
    @CsvSource({
            "PENDING, VERIFIED",
            "PENDING, UNVERIFIED",
            "VERIFIED, SUSPENDED",
            "VERIFIED, UNVERIFIED",
            "SUSPENDED, VERIFIED",
            "SUSPENDED, UNVERIFIED"})
    void adminCanMakeAllowedDecisions(GarageVerificationStatus from, GarageVerificationStatus to) {
        Garage garage = garageIn(from);

        GarageVerificationStatus previous = garage.decideVerification(to, ADMIN, "reviewed", NOW.plusSeconds(10));

        assertThat(previous).isEqualTo(from);
        assertThat(garage.getVerificationStatus()).isEqualTo(to);
        assertThat(garage.getVerificationChangedBy()).isEqualTo(ADMIN);
        assertThat(garage.getVerificationNote()).isEqualTo("reviewed");
    }

    @ParameterizedTest(name = "{0} -> {1} rejected")
    @CsvSource({
            "PENDING, PENDING",
            "PENDING, SUSPENDED",
            "VERIFIED, PENDING",
            "VERIFIED, VERIFIED",
            "UNVERIFIED, VERIFIED",
            "UNVERIFIED, SUSPENDED",
            "SUSPENDED, PENDING"})
    void adminCannotMakeDisallowedDecisions(GarageVerificationStatus from, GarageVerificationStatus to) {
        Garage garage = garageIn(from);

        assertThatThrownBy(() -> garage.decideVerification(to, ADMIN, null, NOW))
                .isInstanceOf(InvalidVerificationTransitionException.class);
        assertThat(garage.getVerificationStatus()).isEqualTo(from);
    }

    @Test
    void garageAdminCanReapplyOnlyFromUnverified() {
        Garage rejected = garageIn(UNVERIFIED);
        rejected.requestVerification(FOUNDER, NOW);
        assertThat(rejected.getVerificationStatus()).isEqualTo(PENDING);

        Garage verified = garageIn(VERIFIED);
        assertThatThrownBy(() -> verified.requestVerification(FOUNDER, NOW))
                .isInstanceOf(InvalidVerificationTransitionException.class);
    }

    @Test
    void suspendedGarageReportsSuspended() {
        assertThat(garageIn(SUSPENDED).isSuspended()).isTrue();
    }

    private static Garage newGarage() {
        return Garage.register(" Garage Test ", "12345678", "Teststraat 1", "1234ab", "Utrecht", "030-1234567",
                "Info@Garage-Test.nl", FOUNDER, NOW);
    }

    /** Drives a new garage into the requested status through legal transitions only. */
    private static Garage garageIn(GarageVerificationStatus status) {
        Garage garage = newGarage();
        switch (status) {
            case PENDING -> { }
            case VERIFIED -> garage.decideVerification(VERIFIED, ADMIN, null, NOW);
            case UNVERIFIED -> garage.decideVerification(UNVERIFIED, ADMIN, null, NOW);
            case SUSPENDED -> {
                garage.decideVerification(VERIFIED, ADMIN, null, NOW);
                garage.decideVerification(SUSPENDED, ADMIN, null, NOW);
            }
        }
        return garage;
    }
}
