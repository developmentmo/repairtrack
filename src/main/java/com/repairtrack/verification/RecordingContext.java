package com.repairtrack.verification;

/**
 * The facts the backend has established about HOW a record is being created. Only server-side
 * code can construct these after its own authorization checks; nothing here comes from a client.
 */
public sealed interface RecordingContext {

    /** The caller is the vehicle's current owner. */
    record ByOwner() implements RecordingContext {
    }

    /** The current owner, with a supporting document attached (Phase 6). */
    record ByOwnerWithDocument() implements RecordingContext {
    }

    /** The caller acts for a garage; {@code garageVerified} comes from the garage module. */
    record ByGarage(boolean garageVerified) implements RecordingContext {
    }

    /** An official/system import (RDW, manufacturer). Not reachable through the public API in V1. */
    record ByOfficialSource(SourceType source) implements RecordingContext {
    }
}
