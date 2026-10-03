package com.repairtrack.security.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Abuse protection, bound from {@code repairtrack.rate-limit.*}. Per-IP limits on the endpoints that can be
 * abused without an account (or to guess things), plus a per-account limit on failed logins.
 *
 * @param enabled                 per-IP limits on/off (off in automated tests, which all come from one IP)
 * @param login                   {@code POST /auth/login} per IP
 * @param register                {@code POST /auth/register} per IP
 * @param refresh                 {@code POST /auth/refresh} per IP
 * @param claim                   {@code POST /vehicles/{id}/claim} per IP (VIN guessing)
 * @param publicReport            {@code GET /public/**} per IP (scraping share links)
 * @param loginFailuresPerAccount failed logins per email address, regardless of IP (password guessing)
 * @param accountEmails           {@code POST /auth/resend-verification} and {@code /auth/forgot-password} per IP
 *                                (mail bombing), each endpoint counted separately
 * @param accountTokens           {@code POST /auth/verify-email} and {@code /auth/reset-password} per IP
 */
@ConfigurationProperties(prefix = "repairtrack.rate-limit")
public record RateLimitProperties(
        Boolean enabled,
        Limit login,
        Limit register,
        Limit refresh,
        Limit claim,
        Limit publicReport,
        Limit loginFailuresPerAccount,
        Limit accountEmails,
        Limit accountTokens
) {

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        login = orDefault(login, 10, Duration.ofMinutes(1));
        register = orDefault(register, 10, Duration.ofHours(1));
        refresh = orDefault(refresh, 30, Duration.ofMinutes(1));
        claim = orDefault(claim, 10, Duration.ofHours(1));
        publicReport = orDefault(publicReport, 60, Duration.ofMinutes(1));
        loginFailuresPerAccount = orDefault(loginFailuresPerAccount, 10, Duration.ofMinutes(15));
        accountEmails = orDefault(accountEmails, 5, Duration.ofHours(1));
        accountTokens = orDefault(accountTokens, 20, Duration.ofHours(1));
    }

    /** At most {@code requests} per {@code per}. */
    public record Limit(int requests, Duration per) {

        public Limit {
            if (requests < 1 || per == null || per.isNegative() || per.isZero()) {
                throw new IllegalStateException("A rate limit needs requests >= 1 and a positive duration");
            }
        }
    }

    private static Limit orDefault(Limit limit, int requests, Duration per) {
        return limit != null ? limit : new Limit(requests, per);
    }
}
