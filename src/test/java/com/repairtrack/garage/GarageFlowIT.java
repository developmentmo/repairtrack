package com.repairtrack.garage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
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
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;

/**
 * Garage registration, membership and verification over real HTTP and PostgreSQL,
 * with the authorization rules exercised from each role's point of view.
 */
@IntegrationTest
class GarageFlowIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private TestAccounts accounts;

    private Account garageAdmin;
    private UUID garageId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
        garageAdmin = accounts.create("Gerda");
        ApiResponse created = registerGarage(garageAdmin, garagePayload());
        assertThat(created.status()).isEqualTo(201);
        garageId = UUID.fromString(created.field("id"));
    }

    // ---------- registration ----------

    @Test
    void registeredGarageIsPendingAndFounderIsGarageAdmin() {
        ApiResponse garage = api.get("/api/v1/garages/" + garageId, garageAdmin.token());
        ApiResponse mine = api.get("/api/v1/garages/mine", garageAdmin.token());

        assertThat(garage.status()).isEqualTo(200);
        assertThat(garage.field("verificationStatus")).isEqualTo("PENDING");
        assertThat(garage.field("postalCode")).isEqualTo("3511 AB");
        assertThat(mine.body().size()).isEqualTo(1);
        assertThat(mine.body().get(0).get("garageId").asString()).isEqualTo(garageId.toString());
        assertThat(mine.body().get(0).get("role").asString()).isEqualTo("GARAGE_ADMIN");
    }

    @Test
    void clientCannotChooseVerificationStatusOnRegistration() {
        Map<String, Object> payload = garagePayload();
        payload.put("verificationStatus", "VERIFIED");

        ApiResponse response = registerGarage(accounts.create("Victor"), payload);

        assertThat(response.status()).isIn(201, 400);
        if (response.status() == 201) {
            assertThat(response.field("verificationStatus")).isEqualTo("PENDING");
        }
    }

    @Test
    void invalidGarageDataIsRejected() {
        Map<String, Object> payload = garagePayload();
        payload.put("kvkNumber", "12AB");
        payload.put("postalCode", "ABCD 12");

        ApiResponse response = registerGarage(accounts.create("Ivo"), payload);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.field("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.field("message")).contains("kvkNumber").contains("postalCode");
    }

    @Test
    void anyAuthenticatedUserCanViewAGarageButNotAnonymous() {
        Account outsider = accounts.create("Otto");

        assertThat(api.get("/api/v1/garages/" + garageId, outsider.token()).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/garages/" + garageId).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/garages/" + UUID.randomUUID(), outsider.token()).field("code"))
                .isEqualTo("GARAGE_NOT_FOUND");
    }

    // ---------- membership ----------

    @Test
    void garageAdminAddsMechanicWhoThenSeesTheGarage() {
        Account mechanic = accounts.create("Max");

        ApiResponse added = addUser(garageAdmin, mechanic.email(), "MECHANIC");
        ApiResponse mechanicsGarages = api.get("/api/v1/garages/mine", mechanic.token());
        ApiResponse members = api.get("/api/v1/garages/" + garageId + "/users", mechanic.token());

        assertThat(added.status()).isEqualTo(201);
        assertThat(added.field("role")).isEqualTo("MECHANIC");
        assertThat(mechanicsGarages.body().get(0).get("role").asString()).isEqualTo("MECHANIC");
        assertThat(members.status()).isEqualTo(200);
        assertThat(members.body().size()).isEqualTo(2);
    }

    @Test
    void mechanicCannotManageMembers() {
        Account mechanic = accounts.create("Max");
        addUser(garageAdmin, mechanic.email(), "MECHANIC");
        Account other = accounts.create("Olaf");

        ApiResponse addAttempt = addUser(mechanic, other.email(), "GARAGE_ADMIN");
        ApiResponse removeAttempt = api.delete("/api/v1/garages/" + garageId + "/users/" + garageAdmin.id(),
                mechanic.token());

        assertThat(addAttempt.status()).isEqualTo(403);
        assertThat(addAttempt.field("code")).isEqualTo("GARAGE_ACCESS_DENIED");
        assertThat(removeAttempt.status()).isEqualTo(403);
    }

    @Test
    void outsiderCannotSeeOrManageMembers() {
        Account outsider = accounts.create("Otto");

        assertThat(api.get("/api/v1/garages/" + garageId + "/users", outsider.token()).status()).isEqualTo(403);
        assertThat(addUser(outsider, outsider.email(), "GARAGE_ADMIN").status()).isEqualTo(403);
    }

    @Test
    void userCannotBeAddedTwiceAndUnknownEmailIsReported() {
        Account mechanic = accounts.create("Max");
        addUser(garageAdmin, mechanic.email(), "MECHANIC");

        ApiResponse duplicate = addUser(garageAdmin, mechanic.email(), "GARAGE_ADMIN");
        ApiResponse unknown = addUser(garageAdmin, "nobody-" + UUID.randomUUID() + "@example.com", "MECHANIC");

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.field("code")).isEqualTo("ALREADY_GARAGE_MEMBER");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.field("code")).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void removedMechanicLosesAccessButMembershipIsKeptAsHistory() {
        Account mechanic = accounts.create("Max");
        addUser(garageAdmin, mechanic.email(), "MECHANIC");

        ApiResponse removed = api.delete("/api/v1/garages/" + garageId + "/users/" + mechanic.id(), garageAdmin.token());

        assertThat(removed.status()).isEqualTo(204);
        assertThat(api.get("/api/v1/garages/mine", mechanic.token()).body().size()).isZero();
        assertThat(api.get("/api/v1/garages/" + garageId + "/users", mechanic.token()).status()).isEqualTo(403);
        Integer historyRows = jdbcTemplate.queryForObject(
                "select count(*) from garage_user where garage_id = ? and user_id = ? and status = 'ENDED'",
                Integer.class, garageId, mechanic.id());
        assertThat(historyRows).isEqualTo(1);
    }

    @Test
    void removedMechanicCanBeAddedAgain() {
        Account mechanic = accounts.create("Max");
        addUser(garageAdmin, mechanic.email(), "MECHANIC");
        api.delete("/api/v1/garages/" + garageId + "/users/" + mechanic.id(), garageAdmin.token());

        ApiResponse readded = addUser(garageAdmin, mechanic.email(), "MECHANIC");

        assertThat(readded.status()).isEqualTo(201);
    }

    @Test
    void lastGarageAdminCannotLeave() {
        ApiResponse response = api.delete("/api/v1/garages/" + garageId + "/users/" + garageAdmin.id(),
                garageAdmin.token());

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.field("code")).isEqualTo("LAST_GARAGE_ADMIN");
    }

    @Test
    void adminCanLeaveWhenAnotherAdminRemains() {
        Account secondAdmin = accounts.create("Sanne");
        addUser(garageAdmin, secondAdmin.email(), "GARAGE_ADMIN");

        ApiResponse response = api.delete("/api/v1/garages/" + garageId + "/users/" + garageAdmin.id(),
                garageAdmin.token());

        assertThat(response.status()).isEqualTo(204);
    }

    // ---------- verification ----------

    @Test
    void onlySystemAdminCanVerifyAGarage() {
        ApiResponse byGarageAdmin = decide(garageAdmin, "VERIFIED");
        Account systemAdmin = accounts.createSystemAdmin("Sys");
        ApiResponse bySystemAdmin = decide(systemAdmin, "VERIFIED");

        assertThat(byGarageAdmin.status()).isEqualTo(403);
        assertThat(byGarageAdmin.field("code")).isEqualTo("SYSTEM_ADMIN_REQUIRED");
        assertThat(bySystemAdmin.status()).isEqualTo(200);
        assertThat(bySystemAdmin.field("verificationStatus")).isEqualTo("VERIFIED");
    }

    @Test
    void invalidVerificationTransitionIsRejected() {
        Account systemAdmin = accounts.createSystemAdmin("Sys");

        ApiResponse response = decide(systemAdmin, "SUSPENDED"); // PENDING -> SUSPENDED is not allowed

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.field("code")).isEqualTo("INVALID_VERIFICATION_TRANSITION");
    }

    @Test
    void rejectedGarageCanReapply() {
        Account systemAdmin = accounts.createSystemAdmin("Sys");
        decide(systemAdmin, "UNVERIFIED");

        ApiResponse reapply = api.post("/api/v1/garages/" + garageId + "/verification-request", Map.of(),
                garageAdmin.token());

        assertThat(reapply.status()).isEqualTo(200);
        assertThat(reapply.field("verificationStatus")).isEqualTo("PENDING");
    }

    @Test
    void suspensionIsVisibleToMembers() {
        Account systemAdmin = accounts.createSystemAdmin("Sys");
        decide(systemAdmin, "VERIFIED");
        decide(systemAdmin, "SUSPENDED");

        JsonNode mine = api.get("/api/v1/garages/mine", garageAdmin.token()).body();

        assertThat(mine.get(0).get("verificationStatus").asString()).isEqualTo("SUSPENDED");
    }

    // ---------- helpers ----------

    private ApiResponse registerGarage(Account account, Map<String, Object> payload) {
        return api.post("/api/v1/garages", payload, account.token());
    }

    private ApiResponse addUser(Account actor, String email, String role) {
        return api.post("/api/v1/garages/" + garageId + "/users", Map.of("email", email, "role", role), actor.token());
    }

    private ApiResponse decide(Account actor, String status) {
        return api.post("/api/v1/garages/" + garageId + "/verification",
                Map.of("status", status, "note", "integration test"), actor.token());
    }

    private static Map<String, Object> garagePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Autobedrijf Test");
        payload.put("kvkNumber", "12345678");
        payload.put("address", "Oudegracht 1");
        payload.put("postalCode", "3511ab");
        payload.put("city", "Utrecht");
        payload.put("phone", "+31 30 123 4567");
        payload.put("email", "info@autobedrijf-test.nl");
        return payload;
    }
}
