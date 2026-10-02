package com.repairtrack.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Vehicle registration, lookup, editing and the ownership lifecycle over real HTTP and PostgreSQL. */
@IntegrationTest
class VehicleFlowIT {

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

    // ---------- owner registration ----------

    @Test
    void ownerRegistersVehicleAndSeesItInTheirList() {
        Account owner = accounts.create("Olga");
        String vin = TestVins.random();

        ApiResponse created = register(owner, payload(vin.toLowerCase(), "12-abc-3"));
        ApiResponse mine = api.get("/api/v1/vehicles", owner.token());

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.field("vin")).isEqualTo(vin);
        assertThat(created.field("licensePlate")).isEqualTo("12ABC3");
        assertThat(created.body().get("ownedByMe").asBoolean()).isTrue();
        assertThat(mine.body().size()).isEqualTo(1);
        assertThat(mine.body().get(0).get("id").asString()).isEqualTo(created.field("id"));
    }

    @Test
    void vinIsUniqueAcrossTheWholePlatform() {
        String vin = TestVins.random();
        register(accounts.create("Olga"), payload(vin, null));

        ApiResponse duplicate = register(accounts.create("Piet"), payload(vin, null));

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.field("code")).isEqualTo("VEHICLE_ALREADY_REGISTERED");
    }

    @Test
    void invalidVinIsRejected() {
        ApiResponse response = register(accounts.create("Olga"), payload("WVWZZZ1KZAO12345", null));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.field("code")).isEqualTo("INVALID_VEHICLE_DATA");
    }

    // ---------- visibility ----------

    @Test
    void vinIsHiddenFromOtherUsersAndSearchResults() {
        Account owner = accounts.create("Olga");
        String vin = TestVins.random();
        String plate = uniquePlate();
        String id = register(owner, payload(vin, plate)).field("id");
        Account stranger = accounts.create("Stan");

        ApiResponse asStranger = api.get("/api/v1/vehicles/" + id, stranger.token());
        ApiResponse byPlate = api.get("/api/v1/vehicles/search?licensePlate=" + plate, stranger.token());
        ApiResponse byVin = api.get("/api/v1/vehicles/search?vin=" + vin, stranger.token());

        assertThat(asStranger.status()).isEqualTo(200);
        assertThat(asStranger.field("vin")).isNull();
        assertThat(asStranger.body().get("ownedByMe").asBoolean()).isFalse();
        assertThat(asStranger.body().get("canEdit").asBoolean()).isFalse();
        assertThat(byPlate.body().size()).isEqualTo(1);
        assertThat(byPlate.body().get(0).get("id").asString()).isEqualTo(id);
        assertThat(byPlate.body().get(0).has("vin")).isFalse();
        assertThat(byVin.body().size()).isEqualTo(1);
    }

    @Test
    void searchNeedsExactlyOneCriterion() {
        Account user = accounts.create("Olga");

        assertThat(api.get("/api/v1/vehicles/search", user.token()).field("code")).isEqualTo("INVALID_VEHICLE_SEARCH");
        assertThat(api.get("/api/v1/vehicles/search?vin=" + TestVins.random() + "&licensePlate=AB12CD", user.token())
                .status()).isEqualTo(400);
    }

    // ---------- editing ----------

    @Test
    void ownerCanEditDetailsButNotTheVin() {
        Account owner = accounts.create("Olga");
        String vin = TestVins.random();
        String id = register(owner, payload(vin, "12ABC3")).field("id");

        Map<String, Object> update = new HashMap<>(Map.of("licensePlate", "99-XYZ-9", "make", "Volkswagen",
                "model", "Golf Variant", "modelYear", 2011, "vin", TestVins.random()));
        ApiResponse updated = api.put("/api/v1/vehicles/" + id, update, owner.token());

        assertThat(updated.status()).isIn(200, 400); // unknown "vin" property is ignored or rejected
        ApiResponse current = api.get("/api/v1/vehicles/" + id, owner.token());
        assertThat(current.field("vin")).isEqualTo(vin);
        if (updated.status() == 200) {
            assertThat(current.field("licensePlate")).isEqualTo("99XYZ9");
            assertThat(current.field("model")).isEqualTo("Golf Variant");
        }
    }

    @Test
    void strangerCannotEditAVehicle() {
        String id = register(accounts.create("Olga"), payload(TestVins.random(), null)).field("id");

        ApiResponse response = api.put("/api/v1/vehicles/" + id,
                Map.of("make", "Hacked", "model", "Car"), accounts.create("Stan").token());

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.field("code")).isEqualTo("VEHICLE_ACCESS_DENIED");
    }

    // ---------- garage registration ----------

    @Test
    void garageMemberRegistersCustomerVehicleWithoutOwner() {
        Account mechanic = accounts.create("Max");
        UUID garageId = createGarage(mechanic);
        Map<String, Object> payload = payload(TestVins.random(), null);
        payload.put("garageId", garageId.toString());

        ApiResponse created = register(mechanic, payload);

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("ownedByMe").asBoolean()).isFalse();
        assertThat(created.body().get("canEdit").asBoolean()).isTrue();
        assertThat(api.get("/api/v1/vehicles", mechanic.token()).body().size()).isZero();
    }

    @Test
    void nonMemberCannotRegisterOnBehalfOfAGarage() {
        UUID garageId = createGarage(accounts.create("Gerda"));
        Map<String, Object> payload = payload(TestVins.random(), null);
        payload.put("garageId", garageId.toString());

        ApiResponse response = register(accounts.create("Stan"), payload);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.field("code")).isEqualTo("GARAGE_ACCESS_DENIED");
    }

    // ---------- claiming & ownership lifecycle ----------

    @Test
    void customerClaimsGarageRegisteredVehicleWithTheCorrectVin() {
        Account mechanic = accounts.create("Max");
        UUID garageId = createGarage(mechanic);
        String vin = TestVins.random();
        Map<String, Object> payload = payload(vin, null);
        payload.put("garageId", garageId.toString());
        String id = register(mechanic, payload).field("id");
        Account customer = accounts.create("Karin");

        ApiResponse wrongVin = claim(customer, id, TestVins.random(), null);
        ApiResponse claimed = claim(customer, id, vin, null);
        ApiResponse garageEdit = api.put("/api/v1/vehicles/" + id, Map.of("make", "X", "model", "Y"), mechanic.token());

        assertThat(wrongVin.status()).isEqualTo(403);
        assertThat(wrongVin.field("code")).isEqualTo("OWNERSHIP_PROOF_INVALID");
        assertThat(claimed.status()).isEqualTo(201);
        assertThat(claimed.field("status")).isEqualTo("ACTIVE");
        assertThat(api.get("/api/v1/vehicles/" + id, customer.token()).body().get("ownedByMe").asBoolean()).isTrue();
        // once the vehicle has an owner, the registering garage can no longer edit it
        assertThat(garageEdit.status()).isEqualTo(403);
    }

    @Test
    void ownedVehicleCannotBeClaimedBySomeoneElse() {
        String vin = TestVins.random();
        String id = register(accounts.create("Olga"), payload(vin, null)).field("id");

        ApiResponse response = claim(accounts.create("Mallory"), id, vin, null);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.field("code")).isEqualTo("VEHICLE_ALREADY_OWNED");
    }

    @Test
    void ownershipPassesToTheNextOwnerAfterItEnds() {
        Account seller = accounts.create("Sam");
        String vin = TestVins.random();
        String id = register(seller, payload(vin, null)).field("id");
        Account buyer = accounts.create("Bea");

        ApiResponse ended = api.post("/api/v1/vehicles/" + id + "/ownership/end", Map.of(), seller.token());
        ApiResponse claimed = claim(buyer, id, vin, null);

        assertThat(ended.status()).isEqualTo(200);
        assertThat(ended.field("status")).isEqualTo("ENDED");
        assertThat(claimed.status()).isEqualTo(201);
        assertThat(api.get("/api/v1/vehicles", seller.token()).body().size()).isZero();
        assertThat(api.get("/api/v1/vehicles/" + id, seller.token()).field("vin")).isNull();
        Integer periods = jdbcTemplate.queryForObject(
                "select count(*) from vehicle_ownership where vehicle_id = ?", Integer.class, UUID.fromString(id));
        assertThat(periods).isEqualTo(2);
    }

    @Test
    void newOwnershipCannotStartBeforeThePreviousEnded() {
        Account seller = accounts.create("Sam");
        String vin = TestVins.random();
        Map<String, Object> payload = payload(vin, null);
        payload.put("ownedSince", "2020-01-01");
        String id = register(seller, payload).field("id");
        api.post("/api/v1/vehicles/" + id + "/ownership/end", Map.of("endDate", "2024-06-01"), seller.token());

        ApiResponse response = claim(accounts.create("Bea"), id, vin, "2024-01-01");

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.field("code")).isEqualTo("INVALID_OWNERSHIP_PERIOD");
    }

    @Test
    void nonOwnerCannotEndOwnership() {
        String id = register(accounts.create("Olga"), payload(TestVins.random(), null)).field("id");

        ApiResponse response = api.post("/api/v1/vehicles/" + id + "/ownership/end", Map.of(),
                accounts.create("Mallory").token());

        assertThat(response.status()).isEqualTo(403);
    }

    // ---------- helpers ----------

    private ApiResponse register(Account account, Map<String, Object> payload) {
        return api.post("/api/v1/vehicles", payload, account.token());
    }

    private ApiResponse claim(Account account, String vehicleId, String vin, String ownedSince) {
        Map<String, Object> body = new HashMap<>();
        body.put("vin", vin);
        if (ownedSince != null) {
            body.put("ownedSince", ownedSince);
        }
        return api.post("/api/v1/vehicles/" + vehicleId + "/claim", body, account.token());
    }

    private UUID createGarage(Account admin) {
        ApiResponse garage = api.post("/api/v1/garages", Map.of("name", "Garage", "kvkNumber", "12345678",
                "address", "Straat 1", "postalCode", "1234AB", "city", "Utrecht"), admin.token());
        assertThat(garage.status()).isEqualTo(201);
        return UUID.fromString(garage.field("id"));
    }

    private static Map<String, Object> payload(String vin, String plate) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("vin", vin);
        if (plate != null) {
            payload.put("licensePlate", plate);
        }
        payload.put("make", "Volkswagen");
        payload.put("model", "Golf");
        payload.put("modelYear", 2010);
        return payload;
    }

    /** Plates are not unique; a random one keeps plate searches in this shared database unambiguous. */
    private static String uniquePlate() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
