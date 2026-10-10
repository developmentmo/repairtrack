package com.repairtrack.security.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.repairtrack.security.domain.LoginSession;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LoginSession s set s.endedAt = :now where s.id = :id and s.endedAt is null")
    int end(@Param("id") UUID id, @Param("now") Instant now);

    /** Logs the user out everywhere (password reset, blocked, deleted). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LoginSession s set s.endedAt = :now where s.userId = :userId and s.endedAt is null")
    int endAllOfUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Sessions without activity since {@code cutoff} are over for good (ended or idle). */
    @Modifying
    @Query("delete from LoginSession s where s.lastActivityAt < :cutoff")
    int deleteInactiveSince(@Param("cutoff") Instant cutoff);
}
