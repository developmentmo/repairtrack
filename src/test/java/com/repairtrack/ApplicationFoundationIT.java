package com.repairtrack;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.ApiTestClient.ApiResponse;

/**
 * Boots the full application against a real PostgreSQL container and verifies the platform
 * foundation: context starts, Flyway migrates, health is public, everything else is protected,
 * errors are uniform.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApplicationFoundationIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    private ApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
    }

    @Test
    void flywayAppliedAllMigrations() {
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        Integer baseline = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success = true", Integer.class);

        assertThat(failed).isZero();
        assertThat(baseline).isEqualTo(1);
    }

    @Test
    void healthEndpointIsPublicAndReportsUp() {
        ApiResponse response = api.get("/actuator/health");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.field("status")).isEqualTo("UP");
    }

    @Test
    void readinessProbeIsPublic() {
        ApiResponse response = api.get("/actuator/health/readiness");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.field("status")).isEqualTo("UP");
    }

    @Test
    void apiRequiresAuthenticationByDefaultWithUniformErrorBody() {
        ApiResponse response = api.get("/api/v1/does-not-exist");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
        assertThat(response.field("code")).isEqualTo("UNAUTHORIZED");
        assertThat(response.field("path")).isEqualTo("/api/v1/does-not-exist");
        assertThat(response.field("timestamp")).isNotBlank();
        assertThat(response.body().get("status").asInt()).isEqualTo(401);
    }

    @Test
    void sensitiveActuatorEndpointsAreNotPubliclyAccessible() {
        assertThat(api.get("/actuator/env").status()).isEqualTo(401);
        assertThat(api.get("/actuator/beans").status()).isEqualTo(401);
    }
}
