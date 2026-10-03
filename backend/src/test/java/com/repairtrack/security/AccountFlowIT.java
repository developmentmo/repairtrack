package com.repairtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.ApiTestClient;
import com.repairtrack.ApiTestClient.ApiResponse;
import com.repairtrack.IntegrationTest;
import com.repairtrack.RecordingAccountMailer;
import com.repairtrack.RecordingAccountMailer.Kind;
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;

/** Email verification, password reset and user administration end-to-end, following the emailed links. */
@IntegrationTest
class AccountFlowIT {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String NEW_PASSWORD = "an even better passphrase";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingAccountMailer mailer;

    private ApiTestClient api;
    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
    }

    // ---------- email verification ----------

    @Test
    void loginIsRefusedUntilTheEmailLinkIsFollowed() {
        String email = uniqueEmail();
        ApiResponse registered = register(email);
        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.body().get("emailVerified").asBoolean()).isFalse();

        ApiResponse beforeVerification = login(email, PASSWORD);
        assertThat(beforeVerification.status()).isEqualTo(403);
        assertThat(beforeVerification.field("code")).isEqualTo("EMAIL_NOT_VERIFIED");

        String token = mailer.lastToken(email, Kind.VERIFICATION).orElseThrow();
        assertThat(api.post("/api/v1/auth/verify-email", Map.of("token", token)).status()).isEqualTo(204);

        assertThat(login(email, PASSWORD).status()).isEqualTo(200);
        ApiResponse reused = api.post("/api/v1/auth/verify-email", Map.of("token", token));
        assertThat(reused.status()).isEqualTo(400);
        assertThat(reused.field("code")).isEqualTo("INVALID_TOKEN");
        assertThat(auditActions(registered.field("id"))).contains("USER_REGISTERED", "USER_EMAIL_VERIFIED");
    }

    @Test
    void wrongPasswordStillLooksLikeWrongCredentialsForUnverifiedAccounts() {
        String email = uniqueEmail();
        register(email);

        ApiResponse response = login(email, "not the password at all");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.field("code")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void resendGivesANewLinkAndInvalidatesTheOldOne() {
        String email = uniqueEmail();
        register(email);
        String first = mailer.lastToken(email, Kind.VERIFICATION).orElseThrow();

        assertThat(api.post("/api/v1/auth/resend-verification", Map.of("email", email)).status()).isEqualTo(202);
        String second = mailer.lastToken(email, Kind.VERIFICATION).orElseThrow();

        assertThat(second).isNotEqualTo(first);
        assertThat(api.post("/api/v1/auth/verify-email", Map.of("token", first)).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/auth/verify-email", Map.of("token", second)).status()).isEqualTo(204);
    }

    @Test
    void requestsForUnknownOrVerifiedAddressesAreAcceptedButSendNothing() {
        String unknown = uniqueEmail();
        Account verified = accounts.create("Vera");

        assertThat(api.post("/api/v1/auth/resend-verification", Map.of("email", unknown)).status()).isEqualTo(202);
        assertThat(api.post("/api/v1/auth/resend-verification", Map.of("email", verified.email())).status())
                .isEqualTo(202);
        assertThat(api.post("/api/v1/auth/forgot-password", Map.of("email", unknown)).status()).isEqualTo(202);

        assertThat(mailer.count(unknown, Kind.VERIFICATION)).isZero();
        assertThat(mailer.count(unknown, Kind.PASSWORD_RESET)).isZero();
        assertThat(mailer.count(verified.email(), Kind.VERIFICATION)).isEqualTo(1); // only the one from registering
    }

    // ---------- password reset ----------

    @Test
    void resetPasswordChangesThePasswordAndLogsOutEverywhere() {
        Account account = accounts.create("Rob");
        ApiResponse session = login(account.email(), PASSWORD);

        assertThat(api.post("/api/v1/auth/forgot-password", Map.of("email", account.email())).status()).isEqualTo(202);
        String token = mailer.lastToken(account.email(), Kind.PASSWORD_RESET).orElseThrow();

        ApiResponse weak = api.post("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", "short"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.field("code")).isEqualTo("INVALID_PASSWORD");

        assertThat(api.post("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .status()).isEqualTo(204);

        assertThat(login(account.email(), PASSWORD).status()).isEqualTo(401);
        assertThat(login(account.email(), NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/auth/refresh", Map.of("refreshToken", session.field("refreshToken"))).status())
                .isEqualTo(401);
        assertThat(api.post("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", PASSWORD))
                .status()).isEqualTo(400);
        assertThat(auditActions(account.id().toString())).contains("USER_PASSWORD_RESET");
    }

    @Test
    void aResetLinkAlsoConfirmsTheEmailAddress() {
        String email = uniqueEmail();
        register(email);

        api.post("/api/v1/auth/forgot-password", Map.of("email", email));
        String token = mailer.lastToken(email, Kind.PASSWORD_RESET).orElseThrow();
        api.post("/api/v1/auth/reset-password", Map.of("token", token, "newPassword", NEW_PASSWORD));

        assertThat(login(email, NEW_PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void aVerificationLinkCannotBeUsedToResetThePassword() {
        String email = uniqueEmail();
        register(email);
        String verificationToken = mailer.lastToken(email, Kind.VERIFICATION).orElseThrow();

        ApiResponse response = api.post("/api/v1/auth/reset-password",
                Map.of("token", verificationToken, "newPassword", NEW_PASSWORD));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.field("code")).isEqualTo("INVALID_TOKEN");
    }

    // ---------- administration ----------

    @Test
    void systemAdminCanBlockAndUnblockAUser() {
        Account admin = accounts.createSystemAdmin("Ada");
        Account user = accounts.create("Ulla");
        ApiResponse session = login(user.email(), PASSWORD);

        ApiResponse found = api.get("/api/v1/admin/users?email=" + user.email().toUpperCase(), admin.token());
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.field("id")).isEqualTo(user.id().toString());
        assertThat(found.body().has("passwordHash")).isFalse();

        assertThat(api.post("/api/v1/admin/users/" + user.id() + "/block", Map.of(), admin.token()).field("status"))
                .isEqualTo("BLOCKED");
        assertThat(login(user.email(), PASSWORD).field("code")).isEqualTo("ACCOUNT_BLOCKED");
        assertThat(api.post("/api/v1/auth/refresh", Map.of("refreshToken", session.field("refreshToken"))).status())
                .isIn(401, 403);

        assertThat(api.post("/api/v1/admin/users/" + user.id() + "/unblock", Map.of(), admin.token()).field("status"))
                .isEqualTo("ACTIVE");
        assertThat(login(user.email(), PASSWORD).status()).isEqualTo(200);
        assertThat(auditActions(user.id().toString())).contains("USER_BLOCKED", "USER_UNBLOCKED");
    }

    @Test
    void onlySystemAdminsAdministerUsersAndNobodyBlocksThemselves() {
        Account admin = accounts.createSystemAdmin("Adam");
        Account user = accounts.create("Otto");

        ApiResponse byUser = api.post("/api/v1/admin/users/" + admin.id() + "/block", Map.of(), user.token());
        ApiResponse lookupByUser = api.get("/api/v1/admin/users?email=" + admin.email(), user.token());
        ApiResponse self = api.post("/api/v1/admin/users/" + admin.id() + "/block", Map.of(), admin.token());

        assertThat(byUser.status()).isEqualTo(403);
        assertThat(byUser.field("code")).isEqualTo("SYSTEM_ADMIN_REQUIRED");
        assertThat(lookupByUser.status()).isEqualTo(403);
        assertThat(self.status()).isEqualTo(422);
        assertThat(self.field("code")).isEqualTo("CANNOT_BLOCK_YOURSELF");
    }

    @Test
    void systemAdminSeesTheGarageVerificationQueue() {
        Account admin = accounts.createSystemAdmin("Alma");
        Account garageOwner = accounts.create("Gerrit");
        ApiResponse garage = api.post("/api/v1/garages", Map.of("name", "Queue Garage " + UUID.randomUUID(),
                "kvkNumber", "12345678", "address", "Straat 1", "postalCode", "1234AB", "city", "Utrecht"),
                garageOwner.token());

        ApiResponse queue = api.get("/api/v1/garages?verificationStatus=PENDING", admin.token());
        ApiResponse forUser = api.get("/api/v1/garages?verificationStatus=PENDING", garageOwner.token());

        assertThat(queue.status()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        queue.body().forEach(entry -> ids.add(entry.get("id").asString()));
        assertThat(ids).contains(garage.field("id"));
        assertThat(forUser.status()).isEqualTo(403);
    }

    // ---------- helpers ----------

    private ApiResponse register(String email) {
        return api.post("/api/v1/auth/register", Map.of(
                "email", email, "password", PASSWORD, "firstName", "Test", "lastName", "User"));
    }

    private ApiResponse login(String email, String password) {
        return api.post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private static String uniqueEmail() {
        return "account-" + UUID.randomUUID() + "@example.com";
    }

    private List<String> auditActions(String userId) {
        return jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = 'USER' and entity_id = ? order by sequence_number",
                String.class, UUID.fromString(userId));
    }
}
