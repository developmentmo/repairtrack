package com.repairtrack.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The provenance matrix from the product spec (section 28). */
class VerificationServiceTest {

    private final VerificationService service = new VerificationService();

    @Test
    void ownerRecordIsUnverified() {
        assertThat(service.determine(new RecordingContext.ByOwner()))
                .isEqualTo(new Provenance(SourceType.OWNER, VerificationStatus.UNVERIFIED));
    }

    @Test
    void ownerWithDocumentIsDocumented() {
        assertThat(service.determine(new RecordingContext.ByOwnerWithDocument()))
                .isEqualTo(new Provenance(SourceType.OWNER_DOCUMENT, VerificationStatus.DOCUMENTED));
    }

    @Test
    void unverifiedGarageRecordIsGarageVerifiedWithGarageSource() {
        assertThat(service.determine(new RecordingContext.ByGarage(false)))
                .isEqualTo(new Provenance(SourceType.GARAGE, VerificationStatus.GARAGE_VERIFIED));
    }

    @Test
    void verifiedGarageRecordHasVerifiedGarageSource() {
        assertThat(service.determine(new RecordingContext.ByGarage(true)))
                .isEqualTo(new Provenance(SourceType.VERIFIED_GARAGE, VerificationStatus.GARAGE_VERIFIED));
    }

    @Test
    void officialSourcesAreOfficial() {
        assertThat(service.determine(new RecordingContext.ByOfficialSource(SourceType.RDW)))
                .isEqualTo(new Provenance(SourceType.RDW, VerificationStatus.OFFICIAL_SOURCE));
        assertThatThrownBy(() -> service.determine(new RecordingContext.ByOfficialSource(SourceType.OWNER)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
