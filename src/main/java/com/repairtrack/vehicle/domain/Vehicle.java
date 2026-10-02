package com.repairtrack.vehicle.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.repairtrack.vehicle.VehicleFieldChange;

/**
 * A physical vehicle, identified by its VIN for its whole life.
 * <p>
 * The VIN is immutable. The license plate is an ordinary, changeable attribute and never an
 * identity. Ownership is modelled separately ({@link VehicleOwnership}); a vehicle has no owner field.
 */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    /** ISO 3779: 17 characters, letters I, O and Q are not used. */
    private static final Pattern VIN_PATTERN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
    private static final Pattern PLATE_PATTERN = Pattern.compile("[A-Z0-9]{1,12}");
    private static final int FIRST_MODEL_YEAR = 1886;

    @Id
    private UUID id;

    @Column(name = "vin", nullable = false, updatable = false, length = 17)
    private String vin;

    @Column(name = "license_plate", length = 12)
    private String licensePlate;

    @Column(name = "make", nullable = false, length = 100)
    private String make;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(name = "first_registration_date")
    private LocalDate firstRegistrationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VehicleStatus status;

    @Column(name = "registered_by", nullable = false, updatable = false)
    private UUID registeredBy;

    /** Garage on whose behalf the vehicle was registered, if any. */
    @Column(name = "registered_by_garage_id", updatable = false)
    private UUID registeredByGarageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Vehicle() {
        // for JPA
    }

    public static Vehicle register(String vin, VehicleDetails details, UUID registeredBy, UUID registeredByGarageId,
                                   Instant now, LocalDate today) {
        Vehicle vehicle = new Vehicle();
        vehicle.id = UUID.randomUUID();
        vehicle.vin = normalizeVin(vin);
        vehicle.applyDetails(details, today);
        vehicle.status = VehicleStatus.ACTIVE;
        vehicle.registeredBy = Objects.requireNonNull(registeredBy, "registeredBy");
        vehicle.registeredByGarageId = registeredByGarageId;
        vehicle.createdAt = Objects.requireNonNull(now, "now");
        vehicle.updatedAt = now;
        return vehicle;
    }

    /**
     * Replaces the mutable attributes. The VIN cannot be changed here: a wrong VIN is an
     * identity problem and needs an explicit correction process, not an edit.
     *
     * @return the attributes that actually changed (empty if nothing changed)
     */
    public List<VehicleFieldChange> updateDetails(VehicleDetails details, Instant now, LocalDate today) {
        List<VehicleFieldChange> changes = new ArrayList<>();
        VehicleDetails before = currentDetails();
        applyDetails(details, today);
        compare(changes, "licensePlate", before.licensePlate(), licensePlate);
        compare(changes, "make", before.make(), make);
        compare(changes, "model", before.model(), model);
        compare(changes, "modelYear", before.modelYear(), modelYear);
        compare(changes, "firstRegistrationDate", before.firstRegistrationDate(), firstRegistrationDate);
        if (!changes.isEmpty()) {
            updatedAt = now;
        }
        return List.copyOf(changes);
    }

    public static String normalizeVin(String vin) {
        if (vin == null) {
            throw new InvalidVehicleDataException("VIN is required.");
        }
        String normalized = vin.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!VIN_PATTERN.matcher(normalized).matches()) {
            throw new InvalidVehicleDataException(
                    "VIN must be 17 characters (letters and digits, without I, O or Q).");
        }
        return normalized;
    }

    /** Uppercase, without dashes/spaces ("12-ABC-3" -> "12ABC3"). Formatting for display is a client concern. */
    public static String normalizeLicensePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            return null;
        }
        String normalized = licensePlate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!PLATE_PATTERN.matcher(normalized).matches()) {
            throw new InvalidVehicleDataException("License plate must contain 1 to 12 letters or digits.");
        }
        return normalized;
    }

    private void applyDetails(VehicleDetails details, LocalDate today) {
        Objects.requireNonNull(details, "details");
        this.licensePlate = normalizeLicensePlate(details.licensePlate());
        this.make = requireText(details.make(), "make");
        this.model = requireText(details.model(), "model");
        this.modelYear = validModelYear(details.modelYear(), today);
        this.firstRegistrationDate = validFirstRegistration(details.firstRegistrationDate(), today);
    }

    private static Integer validModelYear(Integer modelYear, LocalDate today) {
        if (modelYear == null) {
            return null;
        }
        // manufacturers sell next year's models from mid-year on
        if (modelYear < FIRST_MODEL_YEAR || modelYear > today.getYear() + 1) {
            throw new InvalidVehicleDataException("Model year must be between " + FIRST_MODEL_YEAR + " and "
                    + (today.getYear() + 1) + ".");
        }
        return modelYear;
    }

    private static LocalDate validFirstRegistration(LocalDate date, LocalDate today) {
        if (date != null && date.isAfter(today)) {
            throw new InvalidVehicleDataException("First registration date cannot be in the future.");
        }
        return date;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidVehicleDataException(field + " is required.");
        }
        return value.trim();
    }

    public VehicleDetails currentDetails() {
        return new VehicleDetails(licensePlate, make, model, modelYear, firstRegistrationDate);
    }

    private static void compare(List<VehicleFieldChange> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new VehicleFieldChange(field, before == null ? null : before.toString(),
                    after == null ? null : after.toString()));
        }
    }

    public UUID getId() {
        return id;
    }

    public String getVin() {
        return vin;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public Integer getModelYear() {
        return modelYear;
    }

    public LocalDate getFirstRegistrationDate() {
        return firstRegistrationDate;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public UUID getRegisteredBy() {
        return registeredBy;
    }

    public UUID getRegisteredByGarageId() {
        return registeredByGarageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
