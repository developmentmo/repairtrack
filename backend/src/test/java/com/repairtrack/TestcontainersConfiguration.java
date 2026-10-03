package com.repairtrack;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real infrastructure for integration tests, same images as docker-compose.yml:
 * PostgreSQL (never H2) and Garage as S3-compatible document storage.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine");
    static final DockerImageName GARAGE_IMAGE = DockerImageName.parse("dxflrs/garage:v2.3.0");

    public static final String GARAGE_ACCESS_KEY = "GKfedcba9876543210fedcba9876543210";
    public static final String GARAGE_SECRET_KEY = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    public static final String GARAGE_BUCKET = "repairtrack-test";
    static final int GARAGE_S3_PORT = 3900;
    static final int GARAGE_ADMIN_PORT = 3903;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    /** Single-node Garage that creates bucket and key on startup (Garage v2.3+). */
    @Bean
    GenericContainer<?> garageContainer() {
        return new GenericContainer<>(GARAGE_IMAGE)
                .withCopyFileToContainer(MountableFile.forClasspathResource("garage/garage.toml"), "/etc/garage.toml")
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", GARAGE_ACCESS_KEY)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", GARAGE_SECRET_KEY)
                .withEnv("GARAGE_DEFAULT_BUCKET", GARAGE_BUCKET)
                .withCommand("/garage", "server", "--single-node", "--default-bucket")
                .withExposedPorts(GARAGE_S3_PORT, GARAGE_ADMIN_PORT)
                // the image has no shell, so wait on Garage's own health endpoint instead of a port probe
                .waitingFor(Wait.forHttp("/health").forPort(GARAGE_ADMIN_PORT).forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    /** Uploads containing FakeMalwareScanner.MARKER are treated as malware. */
    @Bean
    @Primary
    FakeMalwareScanner fakeMalwareScanner() {
        return new FakeMalwareScanner();
    }

    /** Account emails are recorded instead of sent; tests read the links from here. */
    @Bean
    @Primary
    RecordingAccountMailer recordingAccountMailer() {
        return new RecordingAccountMailer();
    }

    @Bean
    DynamicPropertyRegistrar garageStorageProperties(@Qualifier("garageContainer") GenericContainer<?> garage) {
        return registry -> {
            registry.add("repairtrack.storage.endpoint",
                    () -> "http://" + garage.getHost() + ":" + garage.getMappedPort(GARAGE_S3_PORT));
            registry.add("repairtrack.storage.region", () -> "garage");
            registry.add("repairtrack.storage.access-key", () -> GARAGE_ACCESS_KEY);
            registry.add("repairtrack.storage.secret-key", () -> GARAGE_SECRET_KEY);
            registry.add("repairtrack.storage.bucket", () -> GARAGE_BUCKET);
        };
    }
}
