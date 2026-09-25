package com.repairtrack.security;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only user lookups for other modules. The only way outside the security module to
 * resolve users; exposes no credentials, statuses beyond "active", or mutation.
 */
public interface UserDirectory {

    /** Case-insensitive; returns only active users. */
    Optional<UserSummary> findActiveByEmail(String email);

    Map<UUID, UserSummary> findByIds(Collection<UUID> ids);
}
