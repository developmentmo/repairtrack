package com.repairtrack.verification;

import org.springframework.stereotype.Service;

/**
 * The single place that decides source type and verification status.
 *
 * <pre>
 * owner                      -> OWNER            / UNVERIFIED
 * owner + document           -> OWNER_DOCUMENT   / DOCUMENTED
 * garage (not verified)      -> GARAGE           / GARAGE_VERIFIED
 * garage (verified)          -> VERIFIED_GARAGE  / GARAGE_VERIFIED
 * RDW / manufacturer import  -> RDW|MANUFACTURER / OFFICIAL_SOURCE
 * </pre>
 * Note: "GARAGE_VERIFIED" means "recorded by a garage"; whether that garage itself is verified by
 * RepairTrack is expressed by the source type (GARAGE vs VERIFIED_GARAGE).
 */
@Service
public class VerificationService {

    public Provenance determine(RecordingContext context) {
        return switch (context) {
            case RecordingContext.ByOwner owner ->
                    new Provenance(SourceType.OWNER, VerificationStatus.UNVERIFIED);
            case RecordingContext.ByOwnerWithDocument ownerWithDocument ->
                    new Provenance(SourceType.OWNER_DOCUMENT, VerificationStatus.DOCUMENTED);
            case RecordingContext.ByGarage garage -> new Provenance(
                    garage.garageVerified() ? SourceType.VERIFIED_GARAGE : SourceType.GARAGE,
                    VerificationStatus.GARAGE_VERIFIED);
            case RecordingContext.ByOfficialSource official -> officialSource(official.source());
        };
    }

    private static Provenance officialSource(SourceType source) {
        if (source != SourceType.RDW && source != SourceType.MANUFACTURER) {
            throw new IllegalArgumentException("Not an official source: " + source);
        }
        return new Provenance(source, VerificationStatus.OFFICIAL_SOURCE);
    }
}
