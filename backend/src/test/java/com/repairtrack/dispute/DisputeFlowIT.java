package com.repairtrack.dispute;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
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
import com.repairtrack.FakeMalwareScanner;
import com.repairtrack.IntegrationTest;
import com.repairtrack.RecordingDisputeMailer;
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;
import com.repairtrack.TestVins;

/** Ownership disputes end to end: filing, responding, the admin decision and what changes for both parties. */
@IntegrationTest
class DisputeFlowIT {

    private static final byte[] PDF = "%PDF-1.4\n%RepairTrack test koopcontract\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingDisputeMailer mailer;

    private ApiTestClient api;
    private TestAccounts accounts;
    private Account owner;
    private Account claimant;
    private Account admin;
    private String vin;
    private String vehicleId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
        owner = accounts.create("Olga");
        claimant = accounts.create("Piet");
        admin = accounts.createSystemAdmin("Ada");
        vin = TestVins.random();
        Map<String, Object> vehicle = new HashMap<>(Map.of("vin", vin, "make", "Volkswagen", "model", "Golf"));
        vehicle.put("ownedSince", LocalDate.now().minusMonths(2).toString());
        ApiResponse registered = api.post("/api/v1/vehicles", vehicle, owner.token());
        assertThat(registered.status()).isEqualTo(201);
        vehicleId = registered.field("id");
    }

    @Test
    void upheldDisputeMakesTheClaimantTheOwnerAndLabelsTheWrongfulOwnersRecords() {
        String ownerRecord = repair(owner, "Banden gewisseld");
        String shareToken = api.post("/api/v1/vehicles/" + vehicleId + "/shares", Map.of(), owner.token())
                .field("token");

        ApiResponse filed = open(claimant, vin, PDF);
        assertThat(filed.status()).isEqualTo(201);
        assertThat(filed.field("status")).isEqualTo("OPEN");
        assertThat(filed.field("role")).isEqualTo("CLAIMANT");
        assertThat(filed.body().get("myEvidence").size()).isEqualTo(1);
        String disputeId = filed.field("id");

        // the owner is told, without the claimant's identity
        assertThat(mailer.to(owner.email())).anySatisfy(m -> {
            assertThat(m.subject()).contains("betwist");
            assertThat(m.text()).doesNotContain(claimant.email()).doesNotContain("Piet");
        });
        assertThat(mailer.to(claimant.email())).anySatisfy(m -> assertThat(m.subject()).contains("ontvangen"));

        // the owner sees the dispute, but not the claimant's statement or files
        JsonNode ownerView = mine(owner).get(0);
        assertThat(ownerView.get("role").asString()).isEqualTo("OWNER");
        assertThat(ownerView.get("canRespond").asBoolean()).isTrue();
        assertThat(ownerView.get("myStatement").isNull()).isTrue();
        assertThat(ownerView.get("myEvidence").size()).isZero();
        assertThat(ownerView.toString()).doesNotContain(claimant.email());

        // while it runs: no new share links; existing links show that the ownership is under review
        ApiResponse newShare = api.post("/api/v1/vehicles/" + vehicleId + "/shares", Map.of(), owner.token());
        assertThat(newShare.status()).isEqualTo(409);
        assertThat(newShare.field("code")).isEqualTo("VEHICLE_UNDER_DISPUTE");
        JsonNode report = api.get("/api/v1/public/vehicles/" + shareToken).body();
        assertThat(report.get("vehicle").get("ownershipUnderReview").asBoolean()).isTrue();

        // not reviewable before the owner responded or the deadline passed
        ApiResponse tooEarly = decide(admin, disputeId, "UPHOLD", null);
        assertThat(tooEarly.status()).isEqualTo(409);
        assertThat(tooEarly.field("code")).isEqualTo("DISPUTE_NOT_REVIEWABLE");

        ApiResponse responded = respond(owner, disputeId);
        assertThat(responded.status()).isEqualTo(200);
        assertThat(responded.field("status")).isEqualTo("AWAITING_REVIEW");
        assertThat(respond(owner, disputeId).field("code")).isEqualTo("DISPUTE_CLOSED"); // only once

        // the admin sees both sides and can open the files
        JsonNode queued = findDispute(api.get("/api/v1/admin/disputes", admin.token()).body(), disputeId);
        assertThat(queued.get("reviewable").asBoolean()).isTrue();
        assertThat(queued.get("claimant").get("email").asString()).isEqualTo(claimant.email());
        assertThat(queued.get("evidence").size()).isEqualTo(2);
        String evidenceId = queued.get("evidence").get(0).get("id").asString();
        ApiResponse download = api.get("/api/v1/admin/disputes/" + disputeId + "/evidence/" + evidenceId,
                admin.token());
        assertThat(download.status()).isEqualTo(200);
        assertThat(api.download(download.field("downloadUrl")).body()).isEqualTo(PDF);

        LocalDate since = LocalDate.now().minusYears(1);
        ApiResponse decided = decide(admin, disputeId, "UPHOLD", since);
        assertThat(decided.status()).isEqualTo(200);
        assertThat(decided.field("status")).isEqualTo("UPHELD");

        // ownership moved; the wrongful period is kept as REVOKED and no longer counts
        assertThat(api.get("/api/v1/vehicles", claimant.token()).body().get(0).get("id").asString())
                .isEqualTo(vehicleId);
        assertThat(api.get("/api/v1/vehicles", owner.token()).body().size()).isZero();
        assertThat(jdbcTemplate.queryForList(
                "select status from vehicle_ownership where vehicle_id = ? order by created_at", String.class,
                UUID.fromString(vehicleId))).containsExactly("REVOKED", "ACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "select start_date from vehicle_ownership where vehicle_id = ? and status = 'ACTIVE'",
                LocalDate.class, UUID.fromString(vehicleId))).isEqualTo(since);

        // the wrongful owner's record is labelled and the rightful owner may void it (not correct it)
        ApiResponse record = api.get("/api/v1/repairs/" + ownerRecord, claimant.token());
        assertThat(record.body().get("enteredDuringRevokedOwnership").asBoolean()).isTrue();
        assertThat(record.body().get("canVoid").asBoolean()).isTrue();
        assertThat(record.body().get("canCorrect").asBoolean()).isFalse();
        assertThat(api.post("/api/v1/repairs/" + ownerRecord + "/void", Map.of("reason", "Niet mijn auto"),
                claimant.token()).status()).isEqualTo(200);

        // both parties are told; everything is audited
        assertThat(mailer.to(claimant.email())).anySatisfy(m -> assertThat(m.subject()).contains("toegekend"));
        assertThat(mailer.to(owner.email())).anySatisfy(m -> assertThat(m.subject()).contains("ingetrokken"));
        assertThat(jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = 'OWNERSHIP_DISPUTE' and entity_id = ? "
                        + "order by sequence_number", String.class, UUID.fromString(disputeId)))
                .containsExactly("DISPUTE_OPENED", "DISPUTE_EVIDENCE_ADDED", "DISPUTE_RESPONDED",
                        "DISPUTE_EVIDENCE_ADDED", "DISPUTE_UPHELD");
        assertThat(jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = 'VEHICLE' and entity_id = ? "
                        + "order by sequence_number", String.class, UUID.fromString(vehicleId)))
                .contains("VEHICLE_OWNERSHIP_REVOKED", "VEHICLE_OWNERSHIP_ASSIGNED");

        // the old share link died with the ownership
        assertThat(api.get("/api/v1/public/vehicles/" + shareToken).status()).isEqualTo(404);
    }

    @Test
    void afterTheDeadlineTheAdminCanRejectWithoutAResponse() {
        String disputeId = open(claimant, vin, PDF).field("id");
        jdbcTemplate.update("update ownership_dispute set response_deadline = now() - interval '1 minute' where id = ?",
                UUID.fromString(disputeId));

        assertThat(respond(owner, disputeId).field("code")).isEqualTo("DISPUTE_CLOSED");
        ApiResponse rejected = decide(admin, disputeId, "REJECT", null);

        assertThat(rejected.field("status")).isEqualTo("REJECTED");
        assertThat(api.get("/api/v1/vehicles", owner.token()).body().size()).isEqualTo(1);
        assertThat(api.post("/api/v1/vehicles/" + vehicleId + "/shares", Map.of(), owner.token()).status())
                .isEqualTo(201);
        assertThat(mailer.to(claimant.email())).anySatisfy(m -> assertThat(m.subject()).contains("afgewezen"));
        assertThat(decide(admin, disputeId, "UPHOLD", null).field("code")).isEqualTo("DISPUTE_CLOSED");
    }

    @Test
    void filingNeedsTheVinAFileAndAnOwnedVehicleOfSomeoneElse() {
        ApiResponse wrongVin = open(claimant, TestVins.random(), PDF);
        assertThat(wrongVin.status()).isEqualTo(403);
        assertThat(wrongVin.field("code")).isEqualTo("OWNERSHIP_PROOF_INVALID");

        ApiResponse ownVehicle = open(owner, vin, PDF);
        assertThat(ownVehicle.field("code")).isEqualTo("ALREADY_VEHICLE_OWNER");

        assertThat(open(claimant, vin, PDF).status()).isEqualTo(201);
        ApiResponse twice = open(claimant, vin, PDF);
        assertThat(twice.status()).isEqualTo(409);
        assertThat(twice.field("code")).isEqualTo("DISPUTE_ALREADY_OPEN");

        api.post("/api/v1/vehicles/" + vehicleId + "/ownership/end", Map.of(), owner.token());
        ApiResponse unowned = open(accounts.create("Kees"), vin, PDF);
        assertThat(unowned.status()).isEqualTo(422);
        assertThat(unowned.field("code")).isEqualTo("VEHICLE_NOT_OWNED");
    }

    @Test
    void malwareInEvidenceStoresNothing() {
        ApiResponse infected = open(claimant, vin,
                ("%PDF-1.4\n" + FakeMalwareScanner.MARKER + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));

        assertThat(infected.status()).isEqualTo(422);
        assertThat(infected.field("code")).isEqualTo("MALWARE_DETECTED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from ownership_dispute where vehicle_id = ?",
                Long.class, UUID.fromString(vehicleId))).isZero();
    }

    @Test
    void onlyThePartiesAndAdminsGetAccess() {
        String disputeId = open(claimant, vin, PDF).field("id");
        Account stranger = accounts.create("Kees");

        assertThat(respond(stranger, disputeId).status()).isEqualTo(404);
        assertThat(respond(claimant, disputeId).field("code")).isEqualTo("DISPUTE_ACCESS_DENIED");
        assertThat(decide(owner, disputeId, "REJECT", null).field("code")).isEqualTo("SYSTEM_ADMIN_REQUIRED");
        assertThat(api.get("/api/v1/admin/disputes", claimant.token()).status()).isEqualTo(403);
        assertThat(mine(stranger).size()).isZero();
    }

    // ---------- helpers ----------

    private ApiResponse open(Account actor, String vinProof, byte[] file) {
        return api.postFile("/api/v1/vehicles/" + vehicleId + "/disputes",
                Map.of("vin", vinProof, "statement", "Ik heb deze auto in 2024 gekocht; zie het koopcontract."),
                "koopcontract.pdf", "application/pdf", file, actor.token());
    }

    private ApiResponse respond(Account actor, String disputeId) {
        return api.postFile("/api/v1/disputes/" + disputeId + "/response",
                Map.of("statement", "Ik heb de auto zelf gekocht bij de dealer; zie de factuur."),
                "factuur.pdf", "application/pdf", PDF, actor.token());
    }

    private ApiResponse decide(Account actor, String disputeId, String decision, LocalDate newOwnerSince) {
        Map<String, Object> body = new HashMap<>(Map.of("decision", decision,
                "note", "Beoordeeld op basis van de aangeleverde stukken."));
        if (newOwnerSince != null) {
            body.put("newOwnerSince", newOwnerSince.toString());
        }
        return api.post("/api/v1/admin/disputes/" + disputeId + "/decision", body, actor.token());
    }

    private JsonNode mine(Account actor) {
        return api.get("/api/v1/disputes/mine", actor.token()).body();
    }

    private String repair(Account actor, String title) {
        Map<String, Object> body = Map.of("eventType", "MAINTENANCE", "eventDate", LocalDate.now().toString(),
                "mileage", 50_000, "title", title);
        ApiResponse created = api.post("/api/v1/vehicles/" + vehicleId + "/repairs", body, actor.token());
        assertThat(created.status()).isEqualTo(201);
        return created.field("id");
    }

    private static JsonNode findDispute(JsonNode list, String disputeId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).get("id").asString().equals(disputeId)) {
                return list.get(i);
            }
        }
        throw new AssertionError("dispute not in list: " + disputeId);
    }
}
