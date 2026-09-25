package com.repairtrack;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against a throwaway Testcontainers PostgreSQL
 * (no docker compose needed). Start this class from the IDE.
 */
public class TestRepairTrackApplication {

    public static void main(String[] args) {
        SpringApplication.from(RepairTrackApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }
}
