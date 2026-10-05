package com.repairtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
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
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;
import com.repairtrack.TestVins;

/** Deleting your own account: personal data gone, history kept, vehicles free to be claimed. */
@IntegrationTest
class AccountDeletionIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
    }

    @Test
    void deletingRemovesPersonalDataButKeepsTheVehicleHistory() {
        Account owner = accounts.create("Olga");
        String vin = TestVins.random();
        String vehicleId = api.post("/api/v1/vehicles", Map.of("vin", vin, "make", "Volkswagen", "model", "Golf"),
                owner.token()).field("id");
        String repairId = api.post("/api/v1/vehicles/" + vehicleId + "/repairs", Map.of("eventType", "MAINTENANCE",
                "eventDate", LocalDate.now().toString(), "mileage", 1000, "title", "Olie ververst"), owner.token())
                .field("id");
        String shareToken = api.post("/api/v1/vehicles/" + vehicleId + "/shares", Map.of(), owner.token())
                .field("token");

        ApiResponse wrong = delete(owner, "not my password");
        assertThat(wrong.status()).isEqualTo(403);
        assertThat(wrong.field("code")).isEqualTo("PASSWORD_INCORRECT");

        assertThat(delete(owner, TestAccounts.PASSWORD).status()).isEqualTo(204);

        // personal data gone, the row stays (history keeps a valid reference)
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select email, first_name, last_name, status from app_user where id = ?", owner.id());
        assertThat(row.get("status")).isEqualTo("DELETED");
        assertThat((String) row.get("email")).endsWith("@deleted.invalid").doesNotContain("olga");
        assertThat(row.get("first_name")).isEqualTo("Verwijderd");

        // no way back in: old token, login, refresh
        assertThat(api.get("/api/v1/users/me", owner.token()).status()).isEqualTo(401);
        assertThat(api.post("/api/v1/auth/login", Map.of("email", owner.email(), "password", TestAccounts.PASSWORD))
                .status()).isEqualTo(401);

        // the history stays with the vehicle, which is free for its next owner; the share link is dead
        assertThat(jdbcTemplate.queryForObject("select count(*) from repair_event where id = ?", Long.class,
                UUID.fromString(repairId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from vehicle_ownership where vehicle_id = ?", String.class, UUID.fromString(vehicleId)))
                .isEqualTo("ENDED");
        assertThat(api.get("/api/v1/public/vehicles/" + shareToken).status()).isEqualTo(404);
        Account next = accounts.create("Piet");
        assertThat(api.post("/api/v1/vehicles/" + vehicleId + "/claim", Map.of("vin", vin), next.token()).status())
                .isEqualTo(201);

        assertThat(jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = 'USER' and entity_id = ?", String.class,
                owner.id())).contains("USER_DELETED");

        // the e-mail address is free again
        assertThat(api.post("/api/v1/auth/register", Map.of("email", owner.email(), "password",
                TestAccounts.PASSWORD, "firstName", "Olga", "lastName", "Nieuw")).status()).isEqualTo(201);
    }

    @Test
    void theOnlyAdminOfAGarageWithColleaguesMustHandOverFirst() {
        Account admin = accounts.create("Gerrit");
        Account mechanic = accounts.create("Monteur");
        String garageId = api.post("/api/v1/garages", garagePayload(), admin.token()).field("id");
        api.post("/api/v1/garages/" + garageId + "/users", Map.of("email", mechanic.email(), "role", "MECHANIC"),
                admin.token());

        ApiResponse refused = delete(admin, TestAccounts.PASSWORD);

        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.field("code")).isEqualTo("LAST_GARAGE_ADMIN");
        assertThat(jdbcTemplate.queryForObject("select status from app_user where id = ?", String.class, admin.id()))
                .isEqualTo("ACTIVE");

        // a mechanic can leave: the membership ends, the garage keeps its admin
        assertThat(delete(mechanic, TestAccounts.PASSWORD).status()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "select status from garage_user where garage_id = ? and user_id = ?", String.class,
                UUID.fromString(garageId), mechanic.id())).isEqualTo("ENDED");
    }

    private ApiResponse delete(Account account, String password) {
        return api.post("/api/v1/users/me/delete", Map.of("password", password), account.token());
    }

    private static Map<String, Object> garagePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "Autobedrijf Verwijdertest");
        payload.put("kvkNumber", "87654321");
        payload.put("address", "Oudegracht 2");
        payload.put("postalCode", "3511ab");
        payload.put("city", "Utrecht");
        return payload;
    }
}
