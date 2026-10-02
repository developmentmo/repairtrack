package com.repairtrack.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.ApiTestClient;
import com.repairtrack.ApiTestClient.ApiResponse;
import com.repairtrack.IntegrationTest;
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;
import com.repairtrack.TestVins;

@IntegrationTest
class AuditTrailIT {

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
    void mutationsAcrossModulesAreAudited() {
        Account owner = accounts.create("Olga");
        String vehicleId = api.post("/api/v1/vehicles", Map.of("vin", TestVins.random(), "make", "Kia", "model", "Ceed"),
                owner.token()).field("id");
        api.put("/api/v1/vehicles/" + vehicleId, Map.of("make", "Kia", "model", "Ceed SW"), owner.token());

        assertThat(actions("USER", owner.id())).containsExactly("USER_REGISTERED");
        assertThat(actions("VEHICLE", UUID.fromString(vehicleId)))
                .containsExactly("VEHICLE_REGISTERED", "VEHICLE_OWNERSHIP_STARTED", "VEHICLE_UPDATED");
        String changed = jdbcTemplate.queryForObject("""
                select new_value::text from audit_event
                where entity_id = ? and action = 'VEHICLE_UPDATED'""", String.class, UUID.fromString(vehicleId));
        assertThat(changed).contains("Ceed SW");
    }

    @Test
    void auditEntriesContainNoEmailAddresses() {
        Account owner = accounts.create("Olga");

        Integer withEmail = jdbcTemplate.queryForObject(
                "select count(*) from audit_event where coalesce(old_value::text, '') || coalesce(new_value::text, '') like ?",
                Integer.class, "%" + owner.email() + "%");

        assertThat(withEmail).isZero();
    }

    @Test
    void auditTableIsAppendOnly() {
        Account owner = accounts.create("Olga");

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_event set action = 'X' where entity_id = ?", owner.id()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_event where entity_id = ?", owner.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void onlySystemAdminsCanReadTheAuditTrail() {
        Account owner = accounts.create("Olga");
        Account admin = accounts.createSystemAdmin("Sys");
        String path = "/api/v1/audit-events?entityType=USER&entityId=" + owner.id();

        ApiResponse asOwner = api.get(path, owner.token());
        ApiResponse asAdmin = api.get(path, admin.token());

        assertThat(asOwner.status()).isEqualTo(403);
        assertThat(asAdmin.status()).isEqualTo(200);
        assertThat(asAdmin.body().get(0).get("action").asString()).isEqualTo("USER_REGISTERED");
    }

    private List<String> actions(String entityType, UUID entityId) {
        return jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = ? and entity_id = ? order by sequence_number",
                String.class, entityType, entityId);
    }
}
