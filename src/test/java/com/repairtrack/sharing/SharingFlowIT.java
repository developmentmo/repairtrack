package com.repairtrack.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * Public sharing end-to-end, including spec rules 11 (no private data in the public report) and
 * 12 (revoked links stop working).
 */
@IntegrationTest
class SharingFlowIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Amsterdam"));
    private static final byte[] PDF = "%PDF-1.4\n%invoice\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private TestAccounts accounts;

    private Account owner;
    private String vin;
    private String vehicleId;
    private Account mechanic;
    private String garageId;
    private String garageRepairId;
    private String ownerRepairId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
        owner = accounts.create("Olga");
        vin = TestVins.random();
        vehicleId = api.post("/api/v1/vehicles", Map.of("vin", vin, "licensePlate", "12-ABC-3", "make", "Volkswagen",
                "model", "Golf", "modelYear", 2016), owner.token()).field("id");
        mechanic = accounts.create("Max");
        garageId = api.post("/api/v1/garages", Map.of("name", "Autobedrijf Deelstra", "kvkNumber", "12345678",
                "address", "Straat 1", "postalCode", "1234AB", "city", "Zwolle"), mechanic.token()).field("id");
        garageRepairId = repair(mechanic, garageId, 150_000, TODAY.minusDays(200), "Distributieriem vervangen");
        ownerRepairId = repair(owner, null, 140_000, TODAY.minusDays(30), "Olie ververst");
        api.post("/api/v1/repairs/" + garageRepairId + "/corrections",
                Map.of("mileage", 150_010, "reason", "Typefout"), mechanic.token());
        api.postFile("/api/v1/repairs/" + ownerRepairId + "/documents", Map.of("documentType", "INVOICE"),
                "factuur Olga Tester.pdf", "application/pdf", PDF, owner.token());
    }

    // ---------- creating and using a link ----------

    @Test
    void ownerSharesAndAnyoneWithTheLinkSeesTheHistoryWithoutLoggingIn() {
        ApiResponse created = share(owner, Map.of());
        String token = created.field("token");

        ApiResponse report = api.get("/api/v1/public/vehicles/" + token); // no Authorization header

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.field("url")).isEqualTo("https://test.repairtrack.example/v/" + token);
        assertThat(report.status()).isEqualTo(200);
        JsonNode body = report.body();
        assertThat(body.get("vehicle").get("make").asString()).isEqualTo("Volkswagen");
        assertThat(body.get("vehicle").get("licensePlate").asString()).isEqualTo("12ABC3");
        assertThat(body.get("vehicle").get("registeredOwnerCount").asLong()).isEqualTo(1);
        assertThat(body.get("history").size()).isEqualTo(2);

        JsonNode garageEntry = entry(body, "Distributieriem vervangen");
        assertThat(garageEntry.get("sourceType").asString()).isEqualTo("GARAGE");
        assertThat(garageEntry.get("verificationStatus").asString()).isEqualTo("GARAGE_VERIFIED");
        assertThat(garageEntry.get("garage").get("name").asString()).isEqualTo("Autobedrijf Deelstra");
        assertThat(garageEntry.get("corrections").get(0).get("originalValue").asString()).isEqualTo("150000");
        assertThat(garageEntry.get("corrections").get(0).get("correctedBy").asString()).isEqualTo("Autobedrijf Deelstra");

        JsonNode ownerEntry = entry(body, "Olie ververst");
        assertThat(ownerEntry.get("verificationStatus").asString()).isEqualTo("DOCUMENTED");
        assertThat(ownerEntry.get("documents").get(0).get("documentType").asString()).isEqualTo("INVOICE");

        // 150,010 km earlier, 140,000 km later: reported as an inconsistency, not hidden
        assertThat(body.get("mileage").get("inconsistencies").size()).isEqualTo(1);
        assertThat(body.get("summary").get("mileageInconsistencies").asInt()).isEqualTo(1);
    }

    @Test
    void publicReportExposesNoPrivateDataOrInternalIds() {
        String token = share(owner, Map.of("includeDocuments", true)).field("token");

        String json = api.get("/api/v1/public/vehicles/" + token).body().toString();

        assertThat(json)
                .doesNotContain(owner.email())
                .doesNotContain("Olga")
                .doesNotContain("Tester")
                .doesNotContain(mechanic.email())
                .doesNotContain(owner.id().toString())
                .doesNotContain(mechanic.id().toString())
                .doesNotContain(vehicleId)
                .doesNotContain(garageId)
                .doesNotContain(garageRepairId)
                .doesNotContain(ownerRepairId)
                .doesNotContain(vin)
                .doesNotContain("factuur"); // original file names are not shown
    }

    @Test
    void documentsAreOnlyDownloadableWhenTheOwnerAllowedIt() {
        String withoutDocs = share(owner, Map.of()).field("token");
        String withDocs = share(owner, Map.of("includeDocuments", true)).field("token");

        JsonNode hiddenDoc = entry(api.get("/api/v1/public/vehicles/" + withoutDocs).body(), "Olie ververst")
                .get("documents").get(0);
        JsonNode shownDoc = entry(api.get("/api/v1/public/vehicles/" + withDocs).body(), "Olie ververst")
                .get("documents").get(0);
        String reference = shownDoc.get("reference").asString();

        assertThat(hiddenDoc.get("downloadable").asBoolean()).isFalse();
        assertThat(hiddenDoc.get("reference").isNull()).isTrue();
        assertThat(api.get("/api/v1/public/vehicles/" + withoutDocs + "/documents/" + reference).status()).isEqualTo(404);

        ApiResponse link = api.get("/api/v1/public/vehicles/" + withDocs + "/documents/" + reference);
        HttpResponse<byte[]> file = api.download(link.field("downloadUrl"));
        assertThat(link.status()).isEqualTo(200);
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.body()).isEqualTo(PDF);
        assertThat(file.headers().firstValue("Content-Disposition").orElse("")).doesNotContain("Olga");
    }

    // ---------- links stop working ----------

    @Test
    void revokedLinksNoLongerWork() {
        ApiResponse created = share(owner, Map.of());
        String token = created.field("token");
        String shareId = created.body().get("share").get("id").asString();

        ApiResponse revoked = api.post("/api/v1/shares/" + shareId + "/revoke", Map.of(), owner.token());
        ApiResponse report = api.get("/api/v1/public/vehicles/" + token);

        assertThat(revoked.status()).isEqualTo(200);
        assertThat(revoked.field("status")).isEqualTo("REVOKED");
        assertThat(report.status()).isEqualTo(404);
        assertThat(report.field("code")).isEqualTo("SHARE_NOT_FOUND");
    }

    @Test
    void expiredAndUnknownLinksGiveTheSameAnswer() {
        String token = share(owner, Map.of("validDays", 1)).field("token");
        jdbcTemplate.update("""
                update vehicle_share set created_at = now() - interval '3 days', expires_at = now() - interval '1 day'
                where vehicle_id = ?""", UUID.fromString(vehicleId));

        ApiResponse expired = api.get("/api/v1/public/vehicles/" + token);
        ApiResponse unknown = api.get("/api/v1/public/vehicles/" + "x".repeat(43));

        assertThat(expired.status()).isEqualTo(404);
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(expired.field("message")).isEqualTo(unknown.field("message"));
    }

    @Test
    void linkStopsWorkingWhenTheVehicleIsSold() {
        String token = share(owner, Map.of()).field("token");

        api.post("/api/v1/vehicles/" + vehicleId + "/ownership/end", Map.of(), owner.token());
        Account buyer = accounts.create("Bea");
        api.post("/api/v1/vehicles/" + vehicleId + "/claim", Map.of("vin", vin), buyer.token());

        assertThat(api.get("/api/v1/public/vehicles/" + token).status()).isEqualTo(404);
    }

    // ---------- management ----------

    @Test
    void onlyTheCurrentOwnerManagesLinks() {
        String shareId = share(owner, Map.of()).body().get("share").get("id").asString();
        Account stranger = accounts.create("Stan");

        assertThat(share(stranger, Map.of()).status()).isEqualTo(403);
        assertThat(share(mechanic, Map.of()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/vehicles/" + vehicleId + "/shares", stranger.token()).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/shares/" + shareId + "/revoke", Map.of(), stranger.token()).status()).isEqualTo(403);
    }

    @Test
    void listShowsStatusAndViewsButNeverTheToken() {
        String token = share(owner, Map.of()).field("token");
        api.get("/api/v1/public/vehicles/" + token);
        api.get("/api/v1/public/vehicles/" + token);

        ApiResponse list = api.get("/api/v1/vehicles/" + vehicleId + "/shares", owner.token());

        assertThat(list.status()).isEqualTo(200);
        JsonNode share = list.body().get(0);
        assertThat(share.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(share.get("accessCount").asLong()).isEqualTo(2);
        assertThat(list.body().toString()).doesNotContain(token);
        assertThat(jdbcTemplate.queryForList("select token_hash from vehicle_share where vehicle_id = ?",
                String.class, UUID.fromString(vehicleId))).doesNotContain(token);
    }

    @Test
    void validityIsLimited() {
        assertThat(share(owner, Map.of("validDays", 0)).status()).isEqualTo(400);
        assertThat(share(owner, Map.of("validDays", 400)).status()).isEqualTo(400);
    }

    @Test
    void sharingIsAudited() {
        String shareId = share(owner, Map.of()).body().get("share").get("id").asString();
        api.post("/api/v1/shares/" + shareId + "/revoke", Map.of(), owner.token());

        List<String> actions = jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = 'VEHICLE_SHARE' and entity_id = ? order by sequence_number",
                String.class, UUID.fromString(shareId));
        assertThat(actions).containsExactly("SHARE_CREATED", "SHARE_REVOKED");
    }

    // ---------- helpers ----------

    private ApiResponse share(Account actor, Map<String, Object> body) {
        return api.post("/api/v1/vehicles/" + vehicleId + "/shares", body, actor.token());
    }

    private String repair(Account actor, String garage, int mileage, LocalDate date, String title) {
        Map<String, Object> body = new HashMap<>(Map.of("eventType", "MAINTENANCE", "eventDate", date.toString(),
                "mileage", mileage, "title", title));
        if (garage != null) {
            body.put("garageId", garage);
        }
        ApiResponse created = api.post("/api/v1/vehicles/" + vehicleId + "/repairs", body, actor.token());
        assertThat(created.status()).isEqualTo(201);
        return created.field("id");
    }

    private static JsonNode entry(JsonNode report, String title) {
        for (int i = 0; i < report.get("history").size(); i++) {
            JsonNode entry = report.get("history").get(i);
            if (entry.get("title").asString().equals(title)) {
                return entry;
            }
        }
        throw new AssertionError("No history entry titled " + title);
    }
}
