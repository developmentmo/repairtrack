package com.repairtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.ApiTestClient;
import com.repairtrack.ApiTestClient.ApiResponse;
import com.repairtrack.IntegrationTest;

/**
 * End-to-end authentication and account-security behaviour over real HTTP and PostgreSQL.
 * Every test uses its own unique email, so tests are independent despite the shared database.
 */
@IntegrationTest
class AuthFlowIT {

    private static final String PASSWORD = "correct horse battery staple";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
    }

    // ---------- registration ----------

    @Test
    void registrationCreatesActiveOwnerAndNeverReturnsCredentials() {
        String email = uniqueEmail();

        ApiResponse response = register(email);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.field("email")).isEqualTo(email);
        assertThat(response.field("status")).isEqualTo("ACTIVE");
        assertThat(rolesOf(response.body())).isEqualTo("[\"OWNER\"]");
        assertThat(response.body().has("password")).isFalse();
        assertThat(response.body().has("passwordHash")).isFalse();
    }

    @Test
    void storedPasswordIsHashedNotPlainText() {
        String email = uniqueEmail();
        register(email);

        String hash = jdbcTemplate.queryForObject("select password_hash from app_user where email = ?", String.class, email);

        assertThat(hash).doesNotContain(PASSWORD).startsWith("{bcrypt}");
    }

    @Test
    void clientCannotChooseRolesOrStatusDuringRegistration() {
        String email = uniqueEmail();

        ApiResponse response = api.post("/api/v1/auth/register", Map.of(
                "email", email,
                "password", PASSWORD,
                "firstName", "Eve",
                "lastName", "Escalation",
                "roles", new String[] {"SYSTEM_ADMIN"},
                "status", "ACTIVE"));

        // Unknown properties are either ignored (201) or rejected (400); both are safe.
        // What must never happen is an account with anything other than OWNER.
        assertThat(response.status()).isIn(201, 400);
        if (response.status() == 201) {
            assertThat(rolesOf(response.body())).isEqualTo("[\"OWNER\"]");
        }
        Integer adminRoles = jdbcTemplate.queryForObject("""
                select count(*) from app_user_role r join app_user u on u.id = r.user_id
                where u.email = ? and r.role = 'SYSTEM_ADMIN'""", Integer.class, email);
        assertThat(adminRoles).isZero();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        String email = uniqueEmail();
        register(email);

        ApiResponse response = register(email.toUpperCase());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.field("code")).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void invalidRegistrationInputIsRejected() {
        ApiResponse shortPassword = api.post("/api/v1/auth/register", Map.of(
                "email", uniqueEmail(), "password", "short", "firstName", "A", "lastName", "B"));
        ApiResponse invalidEmail = api.post("/api/v1/auth/register", Map.of(
                "email", "not-an-email", "password", PASSWORD, "firstName", "A", "lastName", "B"));

        assertThat(shortPassword.status()).isEqualTo(400);
        assertThat(shortPassword.field("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(invalidEmail.status()).isEqualTo(400);
        assertThat(invalidEmail.field("code")).isEqualTo("VALIDATION_FAILED");
    }

    // ---------- login & access ----------

    @Test
    void loginReturnsTokensThatGrantAccessToOwnProfile() {
        String email = uniqueEmail();
        register(email);

        ApiResponse login = login(email, PASSWORD);

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.field("tokenType")).isEqualTo("Bearer");
        assertThat(login.field("accessToken")).isNotBlank();
        assertThat(login.field("refreshToken")).isNotBlank();

        ApiResponse me = api.get("/api/v1/users/me", login.field("accessToken"));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.field("email")).isEqualTo(email);
    }

    @Test
    void loginIsCaseInsensitiveForEmail() {
        String email = uniqueEmail();
        register(email);

        assertThat(login(email.toUpperCase(), PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() {
        String email = uniqueEmail();
        register(email);

        ApiResponse wrongPassword = login(email, "definitely the wrong password");
        ApiResponse unknownEmail = login(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
        assertThat(wrongPassword.field("code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownEmail.field("code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.field("message")).isEqualTo(unknownEmail.field("message"));
    }

    @Test
    void protectedEndpointRejectsMissingAndTamperedTokens() {
        String accessToken = registerAndLogin(uniqueEmail()).field("accessToken");
        String tampered = accessToken.substring(0, accessToken.length() - 4) + "AAAA";

        ApiResponse missing = api.get("/api/v1/users/me");
        ApiResponse invalid = api.get("/api/v1/users/me", tampered);
        ApiResponse garbage = api.get("/api/v1/users/me", "not-a-jwt");

        assertThat(missing.status()).isEqualTo(401);
        assertThat(invalid.status()).isEqualTo(401);
        assertThat(garbage.status()).isEqualTo(401);
        assertThat(invalid.field("code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void authenticatedRequestToUnknownPathGetsUniform404() {
        String accessToken = registerAndLogin(uniqueEmail()).field("accessToken");

        ApiResponse response = api.get("/api/v1/does-not-exist", accessToken);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.field("code")).isEqualTo("NOT_FOUND");
    }

    // ---------- refresh tokens ----------

    @Test
    void refreshRotatesTokens() {
        ApiResponse login = registerAndLogin(uniqueEmail());

        ApiResponse refreshed = refresh(login.field("refreshToken"));

        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.field("refreshToken")).isNotEqualTo(login.field("refreshToken"));
        assertThat(api.get("/api/v1/users/me", refreshed.field("accessToken")).status()).isEqualTo(200);
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeSession() {
        ApiResponse login = registerAndLogin(uniqueEmail());
        String original = login.field("refreshToken");
        String successor = refresh(original).field("refreshToken");

        ApiResponse replay = refresh(original);
        ApiResponse successorAfterReplay = refresh(successor);

        assertThat(replay.status()).isEqualTo(401);
        assertThat(replay.field("code")).isEqualTo("INVALID_REFRESH_TOKEN");
        // the legitimate successor is revoked too: we cannot tell thief and owner apart
        assertThat(successorAfterReplay.status()).isEqualTo(401);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        String email = uniqueEmail();
        ApiResponse login = registerAndLogin(email);
        jdbcTemplate.update("""
                update refresh_token set created_at = now() - interval '2 days', expires_at = now() - interval '1 day'
                where user_id = (select id from app_user where email = ?)""", email);

        ApiResponse response = refresh(login.field("refreshToken"));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.field("code")).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        ApiResponse response = refresh("this-token-was-never-issued");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.field("code")).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        ApiResponse login = registerAndLogin(uniqueEmail());

        ApiResponse logout = api.post("/api/v1/auth/logout", Map.of("refreshToken", login.field("refreshToken")));
        ApiResponse refreshAfterLogout = refresh(login.field("refreshToken"));

        assertThat(logout.status()).isEqualTo(204);
        assertThat(refreshAfterLogout.status()).isEqualTo(401);
    }

    @Test
    void logoutWithUnknownTokenIsIdempotent() {
        ApiResponse response = api.post("/api/v1/auth/logout", Map.of("refreshToken", "unknown-token"));

        assertThat(response.status()).isEqualTo(204);
    }

    // ---------- account status ----------

    @Test
    void blockingAUserTakesEffectImmediately() {
        String email = uniqueEmail();
        ApiResponse login = registerAndLogin(email);
        jdbcTemplate.update("update app_user set status = 'BLOCKED' where email = ?", email);

        ApiResponse withExistingAccessToken = api.get("/api/v1/users/me", login.field("accessToken"));
        ApiResponse newLogin = login(email, PASSWORD);
        ApiResponse refreshAttempt = refresh(login.field("refreshToken"));

        assertThat(withExistingAccessToken.status()).isEqualTo(401);
        assertThat(newLogin.status()).isEqualTo(403);
        assertThat(newLogin.field("code")).isEqualTo("ACCOUNT_BLOCKED");
        assertThat(refreshAttempt.status()).isEqualTo(403);
    }

    // ---------- helpers ----------

    /** Registers and confirms the email address directly (AccountFlowIT covers the real email link). */
    private ApiResponse register(String email) {
        ApiResponse response = api.post("/api/v1/auth/register", Map.of(
                "email", email, "password", PASSWORD, "firstName", "Test", "lastName", "User"));
        jdbcTemplate.update("update app_user set email_verified_at = now() where email = lower(?) and email_verified_at is null",
                email);
        return response;
    }

    private ApiResponse login(String email, String password) {
        return api.post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private ApiResponse refresh(String refreshToken) {
        return api.post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    private ApiResponse registerAndLogin(String email) {
        assertThat(register(email).status()).isEqualTo(201);
        ApiResponse login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        return login;
    }

    private static String rolesOf(JsonNode body) {
        return body.get("roles").toString();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
