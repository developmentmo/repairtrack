package com.repairtrack.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.ApiTestClient;
import com.repairtrack.ApiTestClient.ApiResponse;
import com.repairtrack.IntegrationTest;
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;
import com.repairtrack.TestVins;
import com.repairtrack.TestcontainersConfiguration;

/** Document upload, download, integrity and provenance effects against real PostgreSQL and Garage. */
@IntegrationTest
class DocumentFlowIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Amsterdam"));
    private static final byte[] PDF = "%PDF-1.4\n%RepairTrack test invoice\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private S3Client s3;

    private ApiTestClient api;
    private TestAccounts accounts;
    private Account owner;
    private String ownerRepairId;
    private Account mechanic;
    private String garageRepairId;

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
        owner = accounts.create("Olga");
        String vehicleId = api.post("/api/v1/vehicles", Map.of("vin", TestVins.random(), "make", "Toyota",
                "model", "Yaris"), owner.token()).field("id");
        ownerRepairId = createRepair(owner, vehicleId, null);
        mechanic = accounts.create("Max");
        String garageId = api.post("/api/v1/garages", Map.of("name", "Garage Max", "kvkNumber", "12345678",
                "address", "Straat 1", "postalCode", "1234AB", "city", "Utrecht"), mechanic.token()).field("id");
        garageRepairId = createRepair(mechanic, vehicleId, garageId);
    }

    @Test
    void ownerInvoiceIsStoredWithItsSha256AndRaisesTheRecordToDocumented() throws Exception {
        ApiResponse uploaded = upload(owner, ownerRepairId, "INVOICE", "factuur.pdf", PDF);

        assertThat(uploaded.status()).isEqualTo(201);
        assertThat(uploaded.field("sha256")).isEqualTo(sha256(PDF));
        assertThat(uploaded.field("mimeType")).isEqualTo("application/pdf");
        assertThat(uploaded.body().get("fileSize").asLong()).isEqualTo(PDF.length);
        assertThat(uploaded.body().get("repairVerificationRaised").asBoolean()).isTrue();

        ApiResponse repair = api.get("/api/v1/repairs/" + ownerRepairId, owner.token());
        assertThat(repair.field("sourceType")).isEqualTo("OWNER_DOCUMENT");
        assertThat(repair.field("verificationStatus")).isEqualTo("DOCUMENTED");

        Map<String, Object> verification = jdbcTemplate.queryForMap(
                "select previous_status, new_status, method, evidence_id from verification where repair_event_id = ?",
                UUID.fromString(ownerRepairId));
        assertThat(verification.get("previous_status")).isEqualTo("UNVERIFIED");
        assertThat(verification.get("new_status")).isEqualTo("DOCUMENTED");
        assertThat(verification.get("method")).isEqualTo("DOCUMENT_ATTACHED");
        assertThat(verification.get("evidence_id").toString()).isEqualTo(uploaded.field("id"));

        assertThat(auditActions("DOCUMENT", uploaded.field("id"))).containsExactly("DOCUMENT_UPLOADED");
        assertThat(auditActions("REPAIR_EVENT", ownerRepairId)).contains("REPAIR_VERIFICATION_RAISED");
    }

    @Test
    void photoDoesNotRaiseVerification() {
        ApiResponse uploaded = upload(owner, ownerRepairId, "PHOTO", "dashboard.png", PNG);

        assertThat(uploaded.status()).isEqualTo(201);
        assertThat(uploaded.field("mimeType")).isEqualTo("image/png");
        assertThat(api.get("/api/v1/repairs/" + ownerRepairId, owner.token()).field("verificationStatus"))
                .isEqualTo("UNVERIFIED");
    }

    @Test
    void garageCanAttachToItsRecordWithoutChangingItsProvenance() {
        ApiResponse uploaded = upload(mechanic, garageRepairId, "WORK_ORDER", "werkorder.pdf", PDF);

        assertThat(uploaded.status()).isEqualTo(201);
        assertThat(uploaded.body().get("repairVerificationRaised").asBoolean()).isFalse();
        assertThat(api.get("/api/v1/repairs/" + garageRepairId, owner.token()).field("sourceType")).isEqualTo("GARAGE");
    }

    @Test
    void downloadUsesAShortLivedPresignedUrlThatReturnsTheExactBytes() {
        String documentId = upload(owner, ownerRepairId, "INVOICE", "factuur 2026.pdf", PDF).field("id");

        ApiResponse metadata = api.get("/api/v1/documents/" + documentId, owner.token());
        HttpResponse<byte[]> file = api.download(metadata.field("downloadUrl"));

        assertThat(metadata.status()).isEqualTo(200);
        assertThat(metadata.field("downloadUrl")).contains("X-Amz-Signature");
        assertThat(metadata.field("downloadUrlExpiresAt")).isNotBlank();
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.body()).isEqualTo(PDF);
        assertThat(file.headers().firstValue("Content-Disposition")).hasValueSatisfying(
                header -> assertThat(header).startsWith("attachment"));
    }

    @Test
    void integrityCheckDetectsAChangedStoredObject() {
        String documentId = upload(owner, ownerRepairId, "INVOICE", "factuur.pdf", PDF).field("id");
        ApiResponse before = api.get("/api/v1/documents/" + documentId + "/integrity", owner.token());

        String key = jdbcTemplate.queryForObject("select storage_key from document where id = ?", String.class,
                UUID.fromString(documentId));
        byte[] forged = "%PDF-1.4\n%forged invoice with a nicer price\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        s3.putObject(PutObjectRequest.builder().bucket(TestcontainersConfiguration.GARAGE_BUCKET).key(key).build(),
                RequestBody.fromBytes(forged));
        ApiResponse after = api.get("/api/v1/documents/" + documentId + "/integrity", owner.token());

        assertThat(before.body().get("intact").asBoolean()).isTrue();
        assertThat(after.body().get("intact").asBoolean()).isFalse();
        assertThat(after.field("expectedSha256")).isEqualTo(sha256(PDF));
        assertThat(after.field("actualSha256")).isEqualTo(sha256(forged));
    }

    @Test
    void fileTypeIsDecidedByContentNotByNameOrContentType() {
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);

        ApiResponse response = api.postFile("/api/v1/repairs/" + ownerRepairId + "/documents",
                Map.of("documentType", "INVOICE"), "invoice.pdf", "application/pdf", script, owner.token());

        assertThat(response.status()).isEqualTo(415);
        assertThat(response.field("code")).isEqualTo("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void fileNamesAreSanitizedAndNeverPartOfTheStorageKey() {
        String documentId = upload(owner, ownerRepairId, "INVOICE", "../../etc/passwd.pdf", PDF).field("id");

        String fileName = jdbcTemplate.queryForObject("select file_name from document where id = ?", String.class,
                UUID.fromString(documentId));
        String key = jdbcTemplate.queryForObject("select storage_key from document where id = ?", String.class,
                UUID.fromString(documentId));
        assertThat(fileName).isEqualTo("passwd.pdf");
        assertThat(key).isEqualTo("repair-events/" + ownerRepairId + "/" + documentId);
    }

    @Test
    void ownerCannotAttachToAGarageRecordAndStrangersSeeNothing() {
        String documentId = upload(mechanic, garageRepairId, "WORK_ORDER", "wo.pdf", PDF).field("id");
        Account stranger = accounts.create("Stan");

        assertThat(upload(owner, garageRepairId, "INVOICE", "fake.pdf", PDF).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/repairs/" + garageRepairId + "/documents", owner.token()).body().size()).isEqualTo(1);
        assertThat(api.get("/api/v1/repairs/" + garageRepairId + "/documents", stranger.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/documents/" + documentId, stranger.token()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/documents/" + documentId + "/integrity", stranger.token()).status()).isEqualTo(403);
    }

    @Test
    void voidedRecordsAcceptNoDocuments() {
        api.post("/api/v1/repairs/" + ownerRepairId + "/void", Map.of("reason", "Wrong car"), owner.token());

        ApiResponse response = upload(owner, ownerRepairId, "INVOICE", "f.pdf", PDF);

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.field("code")).isEqualTo("REPAIR_ALREADY_VOIDED");
    }

    @Test
    void emptyFilesAndUnknownDocumentTypesAreRejected() {
        assertThat(upload(owner, ownerRepairId, "INVOICE", "empty.pdf", new byte[0]).status()).isEqualTo(400);
        assertThat(upload(owner, ownerRepairId, "SELFIE", "f.pdf", PDF).status()).isEqualTo(400);
    }

    @Test
    void documentsCannotBeDeleted() {
        String documentId = upload(owner, ownerRepairId, "INVOICE", "f.pdf", PDF).field("id");

        assertThat(api.delete("/api/v1/documents/" + documentId, owner.token()).status()).isEqualTo(405);
    }

    // ---------- helpers ----------

    private ApiResponse upload(Account actor, String repairId, String type, String fileName, byte[] content) {
        return api.postFile("/api/v1/repairs/" + repairId + "/documents", Map.of("documentType", type), fileName,
                "application/octet-stream", content, actor.token());
    }

    private String createRepair(Account actor, String vehicleId, String garageId) {
        Map<String, Object> body = new HashMap<>(Map.of("eventType", "MAINTENANCE", "eventDate",
                TODAY.minusDays(3).toString(), "mileage", 50_000, "title", "Annual service"));
        if (garageId != null) {
            body.put("garageId", garageId);
        }
        ApiResponse created = api.post("/api/v1/vehicles/" + vehicleId + "/repairs", body, actor.token());
        assertThat(created.status()).isEqualTo(201);
        return created.field("id");
    }

    private List<String> auditActions(String entityType, String entityId) {
        return jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = ? and entity_id = ? order by sequence_number",
                String.class, entityType, UUID.fromString(entityId));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
