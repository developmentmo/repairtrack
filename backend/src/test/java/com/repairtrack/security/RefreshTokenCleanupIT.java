package com.repairtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.ApiTestClient;
import com.repairtrack.IntegrationTest;
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;
import com.repairtrack.common.crypto.OpaqueTokens;
import com.repairtrack.security.application.RefreshTokenCleanup;

@IntegrationTest
class RefreshTokenCleanupIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenCleanup cleanup;

    @Test
    void deletesOnlyTokensThatExpiredMoreThanADayAgo() {
        Account account = new TestAccounts(new ApiTestClient(port, jsonMapper), jdbcTemplate).create("Cleo");
        Instant now = Instant.now();
        UUID longExpired = insertToken(account.id(), now.minus(40, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS));
        UUID justExpired = insertToken(account.id(), now.minus(31, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS));

        cleanup.deleteExpired();

        assertThat(exists(longExpired)).isFalse();
        assertThat(exists(justExpired)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where user_id = ? and expires_at > now()", Integer.class,
                account.id())).as("the account's current login session").isPositive();
    }

    private UUID insertToken(UUID userId, Instant createdAt, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into refresh_token (id, user_id, family_id, token_hash, created_at, expires_at, version)
                values (?, ?, ?, ?, ?, ?, 0)
                """, id, userId, UUID.randomUUID(), OpaqueTokens.sha256Hex(OpaqueTokens.generate()),
                Timestamp.from(createdAt), Timestamp.from(expiresAt));
        return id;
    }

    private boolean exists(UUID id) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where id = ?", Integer.class, id) > 0;
    }
}
