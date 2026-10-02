package com.repairtrack.repair.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.repairtrack.repair.RepairEventType;
import com.repairtrack.verification.Provenance;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

class RepairEventTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);
    private static final Provenance GARAGE = new Provenance(SourceType.GARAGE, VerificationStatus.GARAGE_VERIFIED);
    private static final Provenance OWNER = new Provenance(SourceType.OWNER, VerificationStatus.UNVERIFIED);

    private final UUID garageId = UUID.randomUUID();
    private final UUID mechanic = UUID.randomUUID();

    @Test
    void recordCarriesBackendProvenance() {
        RepairEvent event = garageRecord(183_421);

        assertThat(event.getSourceType()).isEqualTo(SourceType.GARAGE);
        assertThat(event.getVerificationStatus()).isEqualTo(VerificationStatus.GARAGE_VERIFIED);
        assertThat(event.getStatus()).isEqualTo(RepairStatus.ACTIVE);
    }

    @Test
    void garageSourceRequiresGarageAndOwnerSourceForbidsIt() {
        assertThatThrownBy(() -> RepairEvent.record(UUID.randomUUID(), null, mechanic, RepairEventType.REPAIR, GARAGE,
                TODAY, 1, "x", null, NOW, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepairEvent.record(UUID.randomUUID(), garageId, mechanic, RepairEventType.REPAIR,
                OWNER, TODAY, 1, "x", null, NOW, TODAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventDateInTheFutureIsRejected() {
        assertThatThrownBy(() -> RepairEvent.record(UUID.randomUUID(), garageId, mechanic, RepairEventType.REPAIR,
                GARAGE, TODAY.plusDays(1), 1, "x", null, NOW, TODAY)).isInstanceOf(InvalidRepairDataException.class);
    }

    @Test
    void correctionKeepsOriginalValueReasonAndAuthor() {
        RepairEvent event = garageRecord(183_421);

        List<RepairCorrection> corrections = event.correct(new RepairChanges(null, null, 183_412, null, null),
                "Typing error", mechanic, garageId, NOW.plusSeconds(3600), TODAY);

        assertThat(event.getMileage()).isEqualTo(183_412);
        assertThat(corrections).hasSize(1);
        RepairCorrection correction = corrections.getFirst();
        assertThat(correction.getField()).isEqualTo(CorrectableField.MILEAGE);
        assertThat(correction.getOldValue()).isEqualTo("183421");
        assertThat(correction.getNewValue()).isEqualTo("183412");
        assertThat(correction.getReason()).isEqualTo("Typing error");
        assertThat(correction.getCorrectedByGarageId()).isEqualTo(garageId);
        assertThat(event.getSourceType()).isEqualTo(SourceType.GARAGE); // provenance never changes
    }

    @Test
    void correctionWithoutRealChangesIsRejected() {
        RepairEvent event = garageRecord(1000);

        assertThatThrownBy(() -> event.correct(new RepairChanges(null, null, 1000, "Brake replacement", null),
                "nothing", mechanic, garageId, NOW, TODAY)).isInstanceOf(NoChangesException.class);
    }

    @Test
    void correctionNeedsAReason() {
        RepairEvent event = garageRecord(1000);

        assertThatThrownBy(() -> event.correct(new RepairChanges(null, null, 1001, null, null), " ",
                mechanic, garageId, NOW, TODAY)).isInstanceOf(InvalidRepairDataException.class);
    }

    @Test
    void voidedRecordStaysButCannotBeChangedAgain() {
        RepairEvent event = garageRecord(1000);

        event.voidEvent("Wrong vehicle", mechanic, NOW);

        assertThat(event.isVoided()).isTrue();
        assertThat(event.getVoidReason()).isEqualTo("Wrong vehicle");
        assertThat(event.getMileage()).isEqualTo(1000);
        assertThatThrownBy(() -> event.voidEvent("again", mechanic, NOW))
                .isInstanceOf(RepairAlreadyVoidedException.class);
        assertThatThrownBy(() -> event.correct(new RepairChanges(null, null, 2000, null, null), "fix",
                mechanic, garageId, NOW, TODAY)).isInstanceOf(RepairAlreadyVoidedException.class);
    }

    @Test
    void documentEvidenceRaisesOnlyOwnerRecords() {
        Provenance documented = new Provenance(SourceType.OWNER_DOCUMENT, VerificationStatus.DOCUMENTED);
        RepairEvent ownerRecord = RepairEvent.record(UUID.randomUUID(), null, mechanic, RepairEventType.MAINTENANCE,
                OWNER, TODAY, 1000, "Oil change", null, NOW, TODAY);
        RepairEvent garageRecord = garageRecord(1000);

        Provenance previous = ownerRecord.applyDocumentEvidence(documented, NOW.plusSeconds(5));

        assertThat(previous).isEqualTo(OWNER);
        assertThat(ownerRecord.getSourceType()).isEqualTo(SourceType.OWNER_DOCUMENT);
        assertThat(ownerRecord.getVerificationStatus()).isEqualTo(VerificationStatus.DOCUMENTED);
        assertThat(ownerRecord.applyDocumentEvidence(documented, NOW)).isNull(); // already documented
        assertThat(garageRecord.applyDocumentEvidence(documented, NOW)).isNull();
        assertThat(garageRecord.getSourceType()).isEqualTo(SourceType.GARAGE);
    }

    @Test
    void documentEvidenceCannotRaiseToAnythingElse() {
        RepairEvent ownerRecord = RepairEvent.record(UUID.randomUUID(), null, mechanic, RepairEventType.MAINTENANCE,
                OWNER, TODAY, 1000, "Oil change", null, NOW, TODAY);

        assertThatThrownBy(() -> ownerRecord.applyDocumentEvidence(GARAGE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partsNeedDescriptionAndSaneQuantity() {
        UUID repairId = UUID.randomUUID();

        assertThatThrownBy(() -> RepairPart.add(repairId, "123", "Bosch", " ", 1, mechanic, NOW))
                .isInstanceOf(InvalidRepairDataException.class);
        assertThatThrownBy(() -> RepairPart.add(repairId, "123", "Bosch", "Brake pads", 0, mechanic, NOW))
                .isInstanceOf(InvalidRepairDataException.class);
        assertThat(RepairPart.add(repairId, " ", "Bosch", "Brake pads", 2, mechanic, NOW).getPartNumber()).isNull();
    }

    private RepairEvent garageRecord(int mileage) {
        return RepairEvent.record(UUID.randomUUID(), garageId, mechanic, RepairEventType.REPAIR, GARAGE,
                LocalDate.of(2026, 9, 14), mileage, "Brake replacement", null, NOW, TODAY);
    }
}
