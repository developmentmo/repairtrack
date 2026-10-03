package com.repairtrack.vehicle.infrastructure.registry;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RDW Open Data (Socrata API), no account needed:
 * <ul>
 *   <li>{@code /resource/m9d7-ebf2.json?kenteken=..} "Gekentekende voertuigen": make, trade name, dates, colour</li>
 *   <li>{@code /resource/8ys7-d773.json?kenteken=..} "Gekentekende voertuigen brandstof": one row per fuel</li>
 * </ul>
 * Plates are never logged.
 */
final class RdwOpenDataRegistry implements VehicleRegistry {

    static final String VEHICLES = "m9d7-ebf2";
    static final String FUELS = "8ys7-d773";

    private static final Logger log = LoggerFactory.getLogger(RdwOpenDataRegistry.class);

    private final VehicleRegistryProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient http;

    RdwOpenDataRegistry(VehicleRegistryProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Optional<RegistryVehicle> lookup(String licensePlate) {
        JsonNode vehicles = fetch(VEHICLES, licensePlate);
        if (vehicles.isEmpty()) {
            return Optional.empty();
        }
        JsonNode vehicle = vehicles.get(0);
        return Optional.of(new RegistryVehicle(
                licensePlate,
                text(vehicle, "merk"),
                text(vehicle, "handelsbenaming"),
                text(vehicle, "voertuigsoort"),
                date(vehicle, "datum_eerste_toelating"),
                date(vehicle, "vervaldatum_apk"),
                text(vehicle, "eerste_kleur"),
                fuelTypes(licensePlate)));
    }

    private List<String> fuelTypes(String licensePlate) {
        List<String> fuels = new ArrayList<>();
        JsonNode rows = fetch(FUELS, licensePlate);
        for (int i = 0; i < rows.size(); i++) {
            String fuel = text(rows.get(i), "brandstof_omschrijving");
            if (fuel != null && !fuels.contains(fuel)) {
                fuels.add(fuel);
            }
        }
        return fuels;
    }

    /** One dataset query; always a JSON array, or {@link VehicleRegistryUnavailableException}. */
    private JsonNode fetch(String dataset, String licensePlate) {
        URI uri = URI.create(properties.baseUrl() + "/resource/" + dataset + ".json?kenteken="
                + URLEncoder.encode(licensePlate, StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(properties.readTimeout())
                .header("Accept", "application/json")
                .GET();
        if (properties.appToken() != null) {
            request.header("X-App-Token", properties.appToken());
        }
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("RDW dataset {} answered HTTP {}", dataset, response.statusCode());
                throw new VehicleRegistryUnavailableException(null);
            }
            JsonNode body = jsonMapper.readTree(response.body());
            if (body == null || !body.isArray()) {
                log.warn("RDW dataset {} answered something other than a JSON array", dataset);
                throw new VehicleRegistryUnavailableException(null);
            }
            return body;
        } catch (IOException | JacksonException e) {
            log.warn("RDW dataset {} could not be read: {}", dataset, e.getClass().getSimpleName());
            throw new VehicleRegistryUnavailableException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VehicleRegistryUnavailableException(e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asString().trim();
        return text.isEmpty() ? null : text;
    }

    /** RDW dates are text in {@code yyyyMMdd}; anything else is treated as unknown. */
    private static LocalDate date(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
