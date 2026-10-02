package com.repairtrack.sharing.application;

import java.time.Instant;
import java.util.UUID;

import com.repairtrack.sharing.domain.ShareStatus;

public final class ShareViews {

    private ShareViews() {
    }

    /** Returned once, at creation: the only moment the token exists outside the client. */
    public record CreatedShare(ShareView share, String token, String url) {

        @Override
        public String toString() {
            return "CreatedShare[" + share + "]"; // never log the token
        }
    }

    public record ShareView(UUID id, Instant createdAt, Instant expiresAt, boolean includeDocuments,
                            ShareStatus status, long accessCount, Instant lastAccessedAt) {
    }
}
