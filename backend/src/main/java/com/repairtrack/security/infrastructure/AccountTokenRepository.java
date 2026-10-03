package com.repairtrack.security.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.repairtrack.security.domain.AccountToken;
import com.repairtrack.security.domain.AccountTokenPurpose;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    /** Row lock: a token clicked twice at the same time is used once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountToken> findByTokenHash(String tokenHash);

    /** A new email makes older, unused links of the same kind invalid. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AccountToken t set t.usedAt = :now "
            + "where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOpen(@Param("userId") UUID userId, @Param("purpose") AccountTokenPurpose purpose,
                       @Param("now") Instant now);

    @Modifying
    @Query("delete from AccountToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
