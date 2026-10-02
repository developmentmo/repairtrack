package com.repairtrack.vehicle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.repairtrack.vehicle.VehicleFieldChange;

class VehicleTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final String VIN = "WVWZZZ1KZAW123456";

    @Test
    void registrationNormalizesVinAndPlate() {
        Vehicle vehicle = Vehicle.register(" wvwzzz1kz aw123456 ", details("12-abc-3", 2010), UUID.randomUUID(),
                null, NOW, TODAY);

        assertThat(vehicle.getVin()).isEqualTo(VIN);
        assertThat(vehicle.getLicensePlate()).isEqualTo("12ABC3");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WVWZZZ1KZAW12345", "WVWZZZ1KZAW1234567", "WVWZZZ1KZAO123456", "WVWZZZ1KZAI123456",
            "WVWZZZ1KZAQ123456", "WVWZZZ1KZAW12345!"})
    void invalidVinsAreRejected(String vin) {
        assertThatThrownBy(() -> Vehicle.normalizeVin(vin)).isInstanceOf(InvalidVehicleDataException.class);
    }

    @Test
    void modelYearMayBeNextYearButNotLater() {
        Vehicle.register(VIN, details(null, 2027), UUID.randomUUID(), null, NOW, TODAY);

        assertThatThrownBy(() -> Vehicle.register(VIN, details(null, 2028), UUID.randomUUID(), null, NOW, TODAY))
                .isInstanceOf(InvalidVehicleDataException.class);
        assertThatThrownBy(() -> Vehicle.register(VIN, details(null, 1800), UUID.randomUUID(), null, NOW, TODAY))
                .isInstanceOf(InvalidVehicleDataException.class);
    }

    @Test
    void firstRegistrationInTheFutureIsRejected() {
        VehicleDetails future = new VehicleDetails(null, "VW", "Golf", 2010, TODAY.plusDays(1));

        assertThatThrownBy(() -> Vehicle.register(VIN, future, UUID.randomUUID(), null, NOW, TODAY))
                .isInstanceOf(InvalidVehicleDataException.class);
    }

    @Test
    void updateReportsOnlyChangedFieldsAndKeepsVin() {
        Vehicle vehicle = Vehicle.register(VIN, details("12ABC3", 2010), UUID.randomUUID(), null, NOW, TODAY);

        List<VehicleFieldChange> changes = vehicle.updateDetails(
                new VehicleDetails("99-XYZ-9", "Volkswagen", "Golf", 2010, null), NOW.plusSeconds(60), TODAY);

        assertThat(changes).containsExactly(new VehicleFieldChange("licensePlate", "12ABC3", "99XYZ9"));
        assertThat(vehicle.getVin()).isEqualTo(VIN);
        assertThat(vehicle.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void updateWithoutChangesDoesNotTouchUpdatedAt() {
        Vehicle vehicle = Vehicle.register(VIN, details("12ABC3", 2010), UUID.randomUUID(), null, NOW, TODAY);

        List<VehicleFieldChange> changes = vehicle.updateDetails(details("12-abc-3", 2010), NOW.plusSeconds(60), TODAY);

        assertThat(changes).isEmpty();
        assertThat(vehicle.getUpdatedAt()).isEqualTo(NOW);
    }

    private static VehicleDetails details(String plate, Integer modelYear) {
        return new VehicleDetails(plate, "Volkswagen", "Golf", modelYear, null);
    }
}
