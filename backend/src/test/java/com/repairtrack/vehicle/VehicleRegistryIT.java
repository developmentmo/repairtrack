package com.repairtrack.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * The registry endpoint over HTTP. The test profile switches the RDW off (tests never call it), so this covers
 * security, validation and the "unavailable" answer; {@code RdwOpenDataRegistryTest} covers the RDW client.
 */
@IntegrationTest
class VehicleRegistryIT {

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
    void requiresASignedInUser() {
        assertThat(api.get("/api/v1/vehicle-registry/12ABC3").status()).isEqualTo(401);
    }

    @Test
    void rejectsAnInvalidPlate() {
        ApiResponse response = api.get("/api/v1/vehicle-registry/12_ABC", accounts.create("Olga").token());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.field("code")).isEqualTo("INVALID_VEHICLE_DATA");
    }

    @Test
    void answersUnavailableWhenTheRegistryIsSwitchedOff() {
        ApiResponse response = api.get("/api/v1/vehicle-registry/12-abc-3", accounts.create("Olga").token());

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.field("code")).isEqualTo("REGISTRY_UNAVAILABLE");
    }
}
