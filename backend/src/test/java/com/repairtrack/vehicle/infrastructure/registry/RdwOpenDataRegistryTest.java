package com.repairtrack.vehicle.infrastructure.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** The RDW Open Data client against an in-process fake Socrata server. */
class RdwOpenDataRegistryTest {

    private HttpServer server;
    private final Map<String, String> answers = new ConcurrentHashMap<>();
    private final Map<String, String> lastQuery = new ConcurrentHashMap<>();
    private final Map<String, String> lastAppToken = new ConcurrentHashMap<>();
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/resource/", this::answer);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsMakeModelDatesColourAndFuels() {
        answers.put(RdwOpenDataRegistry.VEHICLES, """
                [{"kenteken":"12ABC3","voertuigsoort":"Personenauto","merk":"VOLKSWAGEN","handelsbenaming":"GOLF",
                  "datum_eerste_toelating":"20150312","vervaldatum_apk":"20270312","eerste_kleur":"GRIJS",
                  "aantal_zitplaatsen":"5"}]""");
        answers.put(RdwOpenDataRegistry.FUELS, """
                [{"kenteken":"12ABC3","brandstof_omschrijving":"Benzine"},
                 {"kenteken":"12ABC3","brandstof_omschrijving":"Elektriciteit"}]""");

        RegistryVehicle vehicle = registry(null).lookup("12ABC3").orElseThrow();

        assertThat(vehicle).isEqualTo(new RegistryVehicle("12ABC3", "VOLKSWAGEN", "GOLF", "Personenauto",
                LocalDate.of(2015, 3, 12), LocalDate.of(2027, 3, 12), "GRIJS", List.of("Benzine", "Elektriciteit")));
        assertThat(lastQuery.get(RdwOpenDataRegistry.VEHICLES)).isEqualTo("kenteken=12ABC3");
        assertThat(lastAppToken).isEmpty();
    }

    @Test
    void missingAndMalformedFieldsBecomeNull() {
        answers.put(RdwOpenDataRegistry.VEHICLES, """
                [{"kenteken":"12ABC3","merk":"  ","datum_eerste_toelating":"2015-03-12"}]""");
        answers.put(RdwOpenDataRegistry.FUELS, "[]");

        RegistryVehicle vehicle = registry(null).lookup("12ABC3").orElseThrow();

        assertThat(vehicle.make()).isNull();
        assertThat(vehicle.model()).isNull();
        assertThat(vehicle.firstRegistrationDate()).isNull();
        assertThat(vehicle.fuelTypes()).isEmpty();
    }

    @Test
    void unknownPlateIsEmpty() {
        answers.put(RdwOpenDataRegistry.VEHICLES, "[]");

        assertThat(registry(null).lookup("ZZ999Z")).isEmpty();
    }

    @Test
    void sendsTheAppTokenWhenConfigured() {
        answers.put(RdwOpenDataRegistry.VEHICLES, "[]");

        registry("token-123").lookup("12ABC3");

        assertThat(lastAppToken.get(RdwOpenDataRegistry.VEHICLES)).isEqualTo("token-123");
    }

    @Test
    void errorsAndGarbageMeanUnavailable() {
        status = 500;
        answers.put(RdwOpenDataRegistry.VEHICLES, "[]");
        assertThatThrownBy(() -> registry(null).lookup("12ABC3"))
                .isInstanceOf(VehicleRegistryUnavailableException.class);

        status = 200;
        answers.put(RdwOpenDataRegistry.VEHICLES, "{\"error\":true}");
        assertThatThrownBy(() -> registry(null).lookup("12ABC3"))
                .isInstanceOf(VehicleRegistryUnavailableException.class);

        answers.put(RdwOpenDataRegistry.VEHICLES, "<html>not json");
        assertThatThrownBy(() -> registry(null).lookup("12ABC3"))
                .isInstanceOf(VehicleRegistryUnavailableException.class);
    }

    @Test
    void unreachableRegistryMeansUnavailable() {
        RdwOpenDataRegistry registry = registry(null);
        server.stop(0);

        assertThatThrownBy(() -> registry.lookup("12ABC3"))
                .isInstanceOfSatisfying(VehicleRegistryUnavailableException.class,
                        e -> assertThat(e.code()).isEqualTo("REGISTRY_UNAVAILABLE"));
    }

    private RdwOpenDataRegistry registry(String appToken) {
        var properties = new VehicleRegistryProperties("rdw",
                "http://localhost:" + server.getAddress().getPort() + "/", appToken,
                Duration.ofSeconds(1), Duration.ofSeconds(2), null, null);
        return new RdwOpenDataRegistry(properties, JsonMapper.builder().build());
    }

    private void answer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String dataset = path.substring("/resource/".length(), path.length() - ".json".length());
        lastQuery.put(dataset, exchange.getRequestURI().getQuery());
        String token = exchange.getRequestHeaders().getFirst("X-App-Token");
        if (token != null) {
            lastAppToken.put(dataset, token);
        }
        byte[] body = answers.getOrDefault(dataset, "[]").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
