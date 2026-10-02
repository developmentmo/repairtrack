package com.repairtrack.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
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
import com.repairtrack.TestVins;

/**
 * The business rules of the product spec (section 36) end-to-end over HTTP and PostgreSQL.
 * Scenario per test: an owner with a vehicle, garage A with a mechanic, garage B with a mechanic.
 */
@IntegrationTest
class RepairFlowIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Amsterdam"));

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private TestAccounts accounts;

    private Account owner;
    private String vehicleId;
    private Account mechanicA;
    private UUID garageA;
    private Account mechanicB;
    private UUID garageB;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
        owner = accounts.create("Olga");
        vehicleId = api.post("/api/v1/vehicles", Map.of("vin", TestVins.random(), "make", "Volkswagen",
                "model", "Golf", "modelYear", 2015), owner.token()).field("id");
        mechanicA = accounts.create("Anton");
        garageA = createGarage(mechanicA);
        mechanicB = accounts.create("Bert");
        garageB = createGarage(mechanicB);
    }

    // ---------- 1-4: creation and provenance ----------

    @Test
    void ownerCreatesRepairThatIsUnverified() {
        ApiResponse created = create(owner, null, 120_000, TODAY.minusDays(10));

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.field("sourceType")).isEqualTo("OWNER");
        assertThat(created.field("verificationStatus")).isEqualTo("UNVERIFIED");
        assertThat(created.body().get("garage").isNull()).isTrue();
    }

    @Test
    void ownerCannotMarkARepairAsGarageVerified() {
        Map<String, Object> body = repairBody(120_000, TODAY.minusDays(10));
        body.put("verificationStatus", "GARAGE_VERIFIED");
        body.put("sourceType", "VERIFIED_GARAGE");

        ApiResponse response = api.post("/api/v1/vehicles/" + vehicleId + "/repairs", body, owner.token());

        assertThat(response.status()).isIn(201, 400); // unknown fields ignored or rejected, never applied
        if (response.status() == 201) {
            assertThat(response.field("verificationStatus")).isEqualTo("UNVERIFIED");
            assertThat(response.field("sourceType")).isEqualTo("OWNER");
        }
    }

    @Test
    void garageCreatesGarageVerifiedRecordAndVerifiedGarageAfterVerification() {
        ApiResponse beforeVerification = create(mechanicA, garageA, 120_000, TODAY.minusDays(20));
        verifyGarage(garageA);
        ApiResponse afterVerification = create(mechanicA, garageA, 125_000, TODAY.minusDays(5));

        assertThat(beforeVerification.field("sourceType")).isEqualTo("GARAGE");
        assertThat(beforeVerification.field("verificationStatus")).isEqualTo("GARAGE_VERIFIED");
        assertThat(beforeVerification.body().get("garage").get("id").asString()).isEqualTo(garageA.toString());
        assertThat(afterVerification.field("sourceType")).isEqualTo("VERIFIED_GARAGE");
        assertThat(afterVerification.field("verificationStatus")).isEqualTo("GARAGE_VERIFIED");
    }

    @Test
    void strangerCannotAddOwnerRecordsToSomeoneElsesVehicle() {
        ApiResponse response = create(accounts.create("Mallory"), null, 1, TODAY);

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void suspendedGarageCannotRecordWork() {
        verifyGarage(garageA);
        decide(garageA, "SUSPENDED");

        ApiResponse response = create(mechanicA, garageA, 1000, TODAY);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.field("code")).isEqualTo("GARAGE_SUSPENDED");
    }

    @Test
    void futureEventDateIsRejected() {
        ApiResponse response = create(owner, null, 1000, TODAY.plusDays(2));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.field("code")).isEqualTo("INVALID_REPAIR_DATA");
    }

    // ---------- 5-6: who may change what ----------

    @Test
    void mechanicCanOnlyActForTheirOwnGarage() {
        ApiResponse forOtherGarage = create(mechanicB, garageA, 1000, TODAY);
        String repairOfA = create(mechanicA, garageA, 1000, TODAY).field("id");
        ApiResponse voidByB = voidRepair(mechanicB, repairOfA);
        ApiResponse correctByB = correct(mechanicB, repairOfA, Map.of("mileage", 999, "reason", "x"));

        assertThat(forOtherGarage.status()).isEqualTo(403);
        assertThat(forOtherGarage.field("code")).isEqualTo("GARAGE_ACCESS_DENIED");
        assertThat(voidByB.status()).isEqualTo(403);
        assertThat(voidByB.field("code")).isEqualTo("REPAIR_ACCESS_DENIED");
        assertThat(correctByB.status()).isEqualTo(403);
    }

    @Test
    void ownerCannotModifyGarageCreatedRecords() {
        String repairId = create(mechanicA, garageA, 150_000, TODAY.minusDays(3)).field("id");

        ApiResponse voidAttempt = voidRepair(owner, repairId);
        ApiResponse correctAttempt = correct(owner, repairId, Map.of("mileage", 100_000, "reason", "nicer"));
        ApiResponse partsAttempt = api.post("/api/v1/repairs/" + repairId + "/parts",
                Map.of("parts", List.of(Map.of("description", "Fake part", "quantity", 1))), owner.token());

        assertThat(voidAttempt.status()).isEqualTo(403);
        assertThat(correctAttempt.status()).isEqualTo(403);
        assertThat(partsAttempt.status()).isEqualTo(403);
        assertThat(get(owner, repairId).body().get("mileage").asInt()).isEqualTo(150_000);
    }

    // ---------- 7-9: no deletion; void and correction are traceable ----------

    @Test
    void historicalRepairsCannotBeDeleted() {
        String repairId = create(owner, null, 1000, TODAY).field("id");

        ApiResponse delete = api.delete("/api/v1/repairs/" + repairId, owner.token());

        assertThat(delete.status()).isEqualTo(405);
        assertThat(get(owner, repairId).status()).isEqualTo(200);
    }

    @Test
    void voidingKeepsTheRecordAndCreatesAnAuditEvent() {
        String repairId = create(mechanicA, garageA, 183_421, TODAY.minusDays(6)).field("id");

        ApiResponse voided = voidRepair(mechanicA, repairId);
        JsonNode history = api.get("/api/v1/vehicles/" + vehicleId + "/repairs", owner.token()).body();

        assertThat(voided.status()).isEqualTo(200);
        assertThat(voided.field("status")).isEqualTo("VOIDED");
        assertThat(voided.field("voidReason")).isEqualTo("Wrong vehicle");
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).get("status").asString()).isEqualTo("VOIDED");
        assertThat(auditActions(repairId)).containsExactly("REPAIR_CREATED", "REPAIR_VOIDED");
        assertThat(voidRepair(mechanicA, repairId).field("code")).isEqualTo("REPAIR_ALREADY_VOIDED");
    }

    @Test
    void correctionKeepsOriginalValueAndCreatesAnAuditEvent() {
        String repairId = create(mechanicA, garageA, 183_421, TODAY.minusDays(6)).field("id");

        ApiResponse corrected = correct(mechanicA, repairId, Map.of("mileage", 183_412, "reason", "Typing error"));

        assertThat(corrected.status()).isEqualTo(200);
        assertThat(corrected.body().get("mileage").asInt()).isEqualTo(183_412);
        JsonNode correction = corrected.body().get("corrections").get(0);
        assertThat(correction.get("field").asString()).isEqualTo("MILEAGE");
        assertThat(correction.get("originalValue").asString()).isEqualTo("183421");
        assertThat(correction.get("correctedValue").asString()).isEqualTo("183412");
        assertThat(correction.get("reason").asString()).isEqualTo("Typing error");
        assertThat(correction.get("correctedByGarage").get("id").asString()).isEqualTo(garageA.toString());
        assertThat(auditActions(repairId)).containsExactly("REPAIR_CREATED", "REPAIR_CORRECTED");
        // the mileage history now has the corrected reading only
        JsonNode readings = api.get("/api/v1/vehicles/" + vehicleId + "/mileage", owner.token()).body().get("readings");
        assertThat(readings.size()).isEqualTo(1);
        assertThat(readings.get(0).get("mileage").asInt()).isEqualTo(183_412);
    }

    @Test
    void ownerCanCorrectOwnRecord() {
        String repairId = create(owner, null, 50_000, TODAY.minusDays(30)).field("id");

        ApiResponse corrected = correct(owner, repairId, Map.of("title", "Oil and filter change", "reason", "More precise"));

        assertThat(corrected.status()).isEqualTo(200);
        assertThat(corrected.body().get("corrections").get(0).get("correctedByGarage").isNull()).isTrue();
    }

    // ---------- 10: mileage anomalies ----------

    @Test
    void lowerMileageIsAcceptedWithAWarning() {
        create(mechanicA, garageA, 150_000, TODAY.minusDays(100));

        ApiResponse lower = create(mechanicA, garageA, 140_000, TODAY.minusDays(10));
        JsonNode mileage = api.get("/api/v1/vehicles/" + vehicleId + "/mileage", owner.token()).body();

        assertThat(lower.status()).isEqualTo(201);
        JsonNode warning = lower.body().get("warnings").get(0);
        assertThat(warning.get("code").asString()).isEqualTo("MILEAGE_DECREASE");
        assertThat(warning.get("earlier").get("mileage").asInt()).isEqualTo(150_000);
        assertThat(mileage.get("anomalies").size()).isEqualTo(1);
    }

    @Test
    void consistentMileageProducesNoWarnings() {
        create(owner, null, 100_000, TODAY.minusDays(100));

        ApiResponse next = create(owner, null, 110_000, TODAY.minusDays(1));

        assertThat(next.body().get("warnings").size()).isZero();
    }

    // ---------- history visibility, parts, garage vehicles ----------

    @Test
    void historyIsVisibleToOwnerAndGaragesThatWorkedOnItOnly() {
        create(mechanicA, garageA, 1000, TODAY);
        Account stranger = accounts.create("Stan");

        assertThat(api.get("/api/v1/vehicles/" + vehicleId + "/repairs", owner.token()).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/vehicles/" + vehicleId + "/repairs", mechanicA.token()).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/vehicles/" + vehicleId + "/repairs", mechanicB.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/vehicles/" + vehicleId + "/repairs", stranger.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/vehicles/" + vehicleId + "/mileage", stranger.token()).status()).isEqualTo(403);
    }

    @Test
    void partsCanBeRecordedWithTheRepairAndAddedLater() {
        Map<String, Object> body = repairBody(90_000, TODAY);
        body.put("garageId", garageA.toString());
        body.put("parts", List.of(Map.of("partNumber", "0986494521", "brand", "Bosch",
                "description", "Brake pads front", "quantity", 1)));
        String repairId = api.post("/api/v1/vehicles/" + vehicleId + "/repairs", body, mechanicA.token()).field("id");

        ApiResponse added = api.post("/api/v1/repairs/" + repairId + "/parts", Map.of("parts",
                List.of(Map.of("description", "Brake fluid DOT4", "quantity", 2))), mechanicA.token());
        ApiResponse parts = api.get("/api/v1/repairs/" + repairId + "/parts", owner.token());

        assertThat(added.status()).isEqualTo(201);
        assertThat(parts.body().size()).isEqualTo(2);
        assertThat(parts.body().get(0).get("brand").asString()).isEqualTo("Bosch");
        assertThat(auditActions(repairId)).containsExactly("REPAIR_CREATED", "REPAIR_PART_ADDED", "REPAIR_PART_ADDED");
    }

    @Test
    void garageSeesTheVehiclesItWorkedOn() {
        create(mechanicA, garageA, 1000, TODAY);

        ApiResponse vehiclesOfA = api.get("/api/v1/garages/" + garageA + "/vehicles", mechanicA.token());
        ApiResponse vehiclesOfAForB = api.get("/api/v1/garages/" + garageA + "/vehicles", mechanicB.token());

        assertThat(vehiclesOfA.body().size()).isEqualTo(1);
        assertThat(vehiclesOfA.body().get(0).get("id").asString()).isEqualTo(vehicleId);
        assertThat(vehiclesOfA.body().get(0).has("vin")).isFalse();
        assertThat(vehiclesOfAForB.status()).isEqualTo(403);
    }

    // ---------- helpers ----------

    private ApiResponse create(Account actor, UUID garageId, int mileage, LocalDate date) {
        Map<String, Object> body = repairBody(mileage, date);
        if (garageId != null) {
            body.put("garageId", garageId.toString());
        }
        return api.post("/api/v1/vehicles/" + vehicleId + "/repairs", body, actor.token());
    }

    private static Map<String, Object> repairBody(int mileage, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("eventType", "REPAIR");
        body.put("eventDate", date.toString());
        body.put("mileage", mileage);
        body.put("title", "Brake replacement");
        body.put("description", "Front discs and pads replaced");
        return body;
    }

    private ApiResponse get(Account actor, String repairId) {
        return api.get("/api/v1/repairs/" + repairId, actor.token());
    }

    private ApiResponse voidRepair(Account actor, String repairId) {
        return api.post("/api/v1/repairs/" + repairId + "/void", Map.of("reason", "Wrong vehicle"), actor.token());
    }

    private ApiResponse correct(Account actor, String repairId, Map<String, Object> body) {
        return api.post("/api/v1/repairs/" + repairId + "/corrections", body, actor.token());
    }

    private UUID createGarage(Account admin) {
        ApiResponse garage = api.post("/api/v1/garages", Map.of("name", "Garage " + admin.email().substring(0, 5),
                "kvkNumber", "12345678", "address", "Straat 1", "postalCode", "1234AB", "city", "Utrecht"), admin.token());
        assertThat(garage.status()).isEqualTo(201);
        return UUID.fromString(garage.field("id"));
    }

    private void verifyGarage(UUID garageId) {
        assertThat(decide(garageId, "VERIFIED").status()).isEqualTo(200);
    }

    private ApiResponse decide(UUID garageId, String status) {
        Account admin = accounts.createSystemAdmin("Sys");
        return api.post("/api/v1/garages/" + garageId + "/verification", Map.of("status", status), admin.token());
    }

    private List<String> auditActions(String repairId) {
        return jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = 'REPAIR_EVENT' and entity_id = ? order by sequence_number",
                String.class, UUID.fromString(repairId));
    }
}
