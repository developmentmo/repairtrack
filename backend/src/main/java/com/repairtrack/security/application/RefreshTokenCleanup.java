package com.repairtrack.security.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.security.infrastructure.AccountTokenRepository;
import com.repairtrack.security.infrastructure.RefreshTokenRepository;

/**
 * Deletes refresh tokens and email-link tokens that expired more than a day ago. They can no longer be used, so keeping them only
 * grows the table. These are credentials, not history: the "no hard deletes" rule is about the vehicle history
 * and its audit trail. Idempotent, so it may run on several instances.
 */
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);
    static final Duration GRACE = Duration.ofDays(1);

    private final RefreshTokenRepository refreshTokens;
    private final AccountTokenRepository accountTokens;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository refreshTokens, AccountTokenRepository accountTokens,
                               Clock clock) {
        this.refreshTokens = refreshTokens;
        this.accountTokens = accountTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "${repairtrack.security.refresh-token-cleanup-cron:0 30 3 * * *}",
            zone = "${repairtrack.business-time-zone:Europe/Amsterdam}")
    @Transactional
    public int deleteExpired() {
        Instant cutoff = Instant.now(clock).minus(GRACE);
        int refresh = refreshTokens.deleteExpiredBefore(cutoff);
        int account = accountTokens.deleteExpiredBefore(cutoff);
        log.info("Token cleanup: {} refresh token(s) and {} email-link token(s) deleted", refresh, account);
        return refresh + account;
    }
}
