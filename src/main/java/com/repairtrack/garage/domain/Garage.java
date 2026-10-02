package com.repairtrack.garage.domain;

import static com.repairtrack.garage.GarageVerificationStatus.PENDING;
import static com.repairtrack.garage.GarageVerificationStatus.SUSPENDED;
import static com.repairtrack.garage.GarageVerificationStatus.UNVERIFIED;
import static com.repairtrack.garage.GarageVerificationStatus.VERIFIED;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.repairtrack.garage.GarageVerificationStatus;

/**
 * A garage (business location) that records work on vehicles.
 * <p>
 * Verification lifecycle (new garages start as PENDING):
 * <ul>
 *   <li>PENDING &rarr; VERIFIED | UNVERIFIED (system admin reviews)</li>
 *   <li>VERIFIED &rarr; SUSPENDED | UNVERIFIED (system admin)</li>
 *   <li>SUSPENDED &rarr; VERIFIED | UNVERIFIED (system admin)</li>
 *   <li>UNVERIFIED &rarr; PENDING (garage admin re-applies)</li>
 * </ul>
 * Only system admins decide; a garage admin can only (re-)apply from UNVERIFIED.
 */
@Entity
@Table(name = "garage")
public class Garage {

    /** Decisions a system admin may take, per current status. */
    private static final Map<GarageVerificationStatus, Set<GarageVerificationStatus>> ADMIN_TRANSITIONS = Map.of(
            PENDING, Set.of(VERIFIED, UNVERIFIED),
            VERIFIED, Set.of(SUSPENDED, UNVERIFIED),
            SUSPENDED, Set.of(VERIFIED, UNVERIFIED),
            UNVERIFIED, Set.of());

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "kvk_number", nullable = false, length = 8)
    private String kvkNumber;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "postal_code", nullable = false, length = 7)
    private String postalCode;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private GarageVerificationStatus verificationStatus;

    @Column(name = "verification_changed_at", nullable = false)
    private Instant verificationChangedAt;

    @Column(name = "verification_changed_by", nullable = false)
    private UUID verificationChangedBy;

    @Column(name = "verification_note", length = 500)
    private String verificationNote;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Garage() {
        // for JPA
    }

    /** Registers a new garage. It always starts as PENDING; clients cannot choose a status. */
    public static Garage register(String name, String kvkNumber, String address, String postalCode, String city,
                                  String phone, String email, UUID registeredBy, Instant now) {
        Garage garage = new Garage();
        garage.id = UUID.randomUUID();
        garage.name = requireText(name, "name");
        garage.kvkNumber = requireKvkNumber(kvkNumber);
        garage.address = requireText(address, "address");
        garage.postalCode = normalizePostalCode(postalCode);
        garage.city = requireText(city, "city");
        garage.phone = blankToNull(phone);
        garage.email = blankToNull(email) == null ? null : email.trim().toLowerCase(Locale.ROOT);
        garage.verificationStatus = PENDING;
        garage.verificationChangedAt = Objects.requireNonNull(now, "now");
        garage.verificationChangedBy = Objects.requireNonNull(registeredBy, "registeredBy");
        garage.createdBy = registeredBy;
        garage.createdAt = now;
        garage.updatedAt = now;
        return garage;
    }

    /** Garage admin re-applies after a rejection. Only allowed from UNVERIFIED. */
    public GarageVerificationStatus requestVerification(UUID requestedBy, Instant now) {
        if (verificationStatus != UNVERIFIED) {
            throw new InvalidVerificationTransitionException(verificationStatus, PENDING);
        }
        return changeVerification(PENDING, requestedBy, null, now);
    }

    /** System admin decision. Returns the previous status. */
    public GarageVerificationStatus decideVerification(GarageVerificationStatus target, UUID decidedBy,
                                                       String note, Instant now) {
        Objects.requireNonNull(target, "target");
        if (!ADMIN_TRANSITIONS.getOrDefault(verificationStatus, Set.of()).contains(target)) {
            throw new InvalidVerificationTransitionException(verificationStatus, target);
        }
        return changeVerification(target, decidedBy, blankToNull(note), now);
    }

    public boolean isSuspended() {
        return verificationStatus == SUSPENDED;
    }

    private GarageVerificationStatus changeVerification(GarageVerificationStatus target, UUID changedBy,
                                                        String note, Instant now) {
        GarageVerificationStatus previous = verificationStatus;
        verificationStatus = target;
        verificationChangedAt = now;
        verificationChangedBy = Objects.requireNonNull(changedBy, "changedBy");
        verificationNote = note;
        updatedAt = now;
        return previous;
    }

    /** Dutch postal code, normalized to "1234 AB". */
    static String normalizePostalCode(String postalCode) {
        String compact = requireText(postalCode, "postalCode").replace(" ", "").toUpperCase(Locale.ROOT);
        if (!compact.matches("[1-9][0-9]{3}[A-Z]{2}")) {
            throw new InvalidGarageDataException("postalCode must be a Dutch postal code like 1234 AB");
        }
        return compact.substring(0, 4) + " " + compact.substring(4);
    }

    private static String requireKvkNumber(String kvkNumber) {
        String value = requireText(kvkNumber, "kvkNumber");
        if (!value.matches("[0-9]{8}")) {
            throw new InvalidGarageDataException("kvkNumber must be 8 digits");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidGarageDataException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getKvkNumber() {
        return kvkNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public GarageVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public Instant getVerificationChangedAt() {
        return verificationChangedAt;
    }

    public UUID getVerificationChangedBy() {
        return verificationChangedBy;
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
