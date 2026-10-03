package com.repairtrack;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.repairtrack.ApiTestClient.ApiResponse;

/** Creates real accounts through the public API for integration tests. */
public final class TestAccounts {

    public static final String PASSWORD = "correct horse battery staple";

    private final ApiTestClient api;
    private final JdbcTemplate jdbcTemplate;

    public TestAccounts(ApiTestClient api, JdbcTemplate jdbcTemplate) {
        this.api = api;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Registers and logs in a new user with a unique email. */
    public Account create(String firstName) {
        String email = firstName.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        ApiResponse registered = api.post("/api/v1/auth/register", Map.of(
                "email", email, "password", PASSWORD, "firstName", firstName, "lastName", "Tester"));
        assertThat(registered.status()).as("register %s", email).isEqualTo(201);
        // Shortcut for tests that are not about verification (AccountFlowIT covers the real email link).
        jdbcTemplate.update("update app_user set email_verified_at = now() where email = ?", email);
        ApiResponse login = api.post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD));
        assertThat(login.status()).as("login %s", email).isEqualTo(200);
        return new Account(UUID.fromString(registered.field("id")), email, login.field("accessToken"));
    }

    /**
     * Grants SYSTEM_ADMIN directly in the database. There is intentionally no API for this;
     * the existing token keeps working because roles are loaded per request.
     */
    public Account createSystemAdmin(String firstName) {
        Account account = create(firstName);
        jdbcTemplate.update("insert into app_user_role (user_id, role) values (?, 'SYSTEM_ADMIN')", account.id());
        return account;
    }

    public record Account(UUID id, String email, String token) {
    }
}
