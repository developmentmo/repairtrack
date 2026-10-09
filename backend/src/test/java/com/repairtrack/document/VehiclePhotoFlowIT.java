package com.repairtrack.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
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
import com.repairtrack.FakeMalwareScanner;
import com.repairtrack.IntegrationTest;
import com.repairtrack.TestAccounts;
import com.repairtrack.TestAccounts.Account;
import com.repairtrack.TestVins;

/** Vehicle photos against real PostgreSQL and Garage: upload, replace, view, and who may see them. */
@IntegrationTest
class VehiclePhotoFlowIT {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] WEBP = "RIFF$\u0000\u0000\u0000WEBPVP8 test".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PDF = "%PDF-1.4\n%not a photo\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

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

    @BeforeEach
    void setUp() {
        api = new ApiTestClient(port, jsonMapper);
        accounts = new TestAccounts(api, jdbcTemplate);
        owner = accounts.create("Olga");
        vin = TestVins.random();
        vehicleId = api.post("/api/v1/vehicles", Map.of("vin", vin, "make", "Toyota", "model", "Yaris"),
                owner.token()).field("id");
    }

    @Test
    void ownerUploadsAndViewsTheirPhoto() {
        ApiResponse uploaded = upload(owner, "mijn auto.jpg", JPEG);

        assertThat(uploaded.status()).isEqualTo(201);
        assertThat(uploaded.field("vehicleId")).isEqualTo(vehicleId);
        assertThat(uploaded.field("mimeType")).isEqualTo("image/jpeg");
        assertThat(uploaded.field("sha256")).isEqualTo(sha256(JPEG));
        assertThat(uploaded.body().get("fileSize").asLong()).isEqualTo(JPEG.length);

        ApiResponse current = api.get(photoPath(), owner.token());
        HttpResponse<byte[]> file = api.download(current.field("downloadUrl"));

        assertThat(current.status()).isEqualTo(200);
        assertThat(current.field("id")).isEqualTo(uploaded.field("id"));
        assertThat(current.field("downloadUrl")).contains("X-Amz-Signature");
        assertThat(current.field("downloadUrlExpiresAt")).isNotBlank();
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.body()).isEqualTo(JPEG);

        String key = jdbcTemplate.queryForObject("select storage_key from vehicle_photo where id = ?", String.class,
                UUID.fromString(uploaded.field("id")));
        assertThat(key).isEqualTo("vehicles/" + vehicleId + "/photos/" + uploaded.field("id"));
        assertThat(auditActions()).contains("VEHICLE_PHOTO_UPLOADED");
    }

    @Test
    void replacingKeepsTheOldPhotoAsReplaced() {
        String first = upload(owner, "oud.png", PNG).field("id");

        ApiResponse second = upload(owner, "nieuw.webp", WEBP);

        assertThat(second.status()).isEqualTo(201);
        assertThat(second.field("mimeType")).isEqualTo("image/webp");
        assertThat(api.get(photoPath(), owner.token()).field("id")).isEqualTo(second.field("id"));
        Map<String, Object> old = jdbcTemplate.queryForMap(
                "select status, replaced_by_id, replaced_at from vehicle_photo where id = ?", UUID.fromString(first));
        assertThat(old.get("status")).isEqualTo("REPLACED");
        assertThat(old.get("replaced_by_id").toString()).isEqualTo(second.field("id"));
        assertThat(old.get("replaced_at")).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from vehicle_photo where vehicle_id = ?",
                Integer.class, UUID.fromString(vehicleId))).isEqualTo(2);
        assertThat(auditActions()).containsSubsequence("VEHICLE_PHOTO_UPLOADED", "VEHICLE_PHOTO_REPLACED");
    }

    @Test
    void noPhotoYetIsNotFound() {
        ApiResponse response = api.get(photoPath(), owner.token());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.field("code")).isEqualTo("VEHICLE_PHOTO_NOT_FOUND");
    }

    @Test
    void onlyTheOwnerCanUploadOrSeeThePhoto() {
        upload(owner, "auto.jpg", JPEG);
        Account stranger = accounts.create("Stan");
        Account admin = accounts.createSystemAdmin("Ada");

        assertThat(upload(stranger, "nep.jpg", JPEG).status()).isEqualTo(403);
        assertThat(api.get(photoPath(), stranger.token()).status()).isEqualTo(403);
        assertThat(api.get(photoPath(), admin.token()).status()).isEqualTo(403);
        assertThat(api.get(photoPath()).status()).isEqualTo(401);
    }

    @Test
    void afterASaleNeitherOwnerSeesTheOthersPhoto() {
        upload(owner, "auto.jpg", JPEG);
        api.post("/api/v1/vehicles/" + vehicleId + "/ownership/end", Map.of(), owner.token());
        Account buyer = accounts.create("Bert");
        assertThat(api.post("/api/v1/vehicles/" + vehicleId + "/claim", Map.of("vin", vin), buyer.token()).status())
                .isEqualTo(201);

        assertThat(api.get(photoPath(), owner.token()).status()).isEqualTo(403);
        assertThat(api.get(photoPath(), buyer.token()).field("code")).isEqualTo("VEHICLE_PHOTO_NOT_FOUND");
        assertThat(upload(buyer, "mijn.png", PNG).status()).isEqualTo(201);
    }

    @Test
    void onlyJpegPngAndWebpAreAccepted() {
        ApiResponse response = upload(owner, "auto.jpg", PDF);

        assertThat(response.status()).isEqualTo(415);
        assertThat(response.field("code")).isEqualTo("UNSUPPORTED_FILE_TYPE");
        assertThat(upload(owner, "leeg.jpg", new byte[0]).status()).isEqualTo(400);
    }

    @Test
    void malwareIsRejectedAndAuditedWithoutStoringAnything() {
        byte[] infected = ("ÿØÿà " + FakeMalwareScanner.MARKER).getBytes(StandardCharsets.ISO_8859_1);

        ApiResponse response = upload(owner, "auto.jpg", infected);

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.field("code")).isEqualTo("MALWARE_DETECTED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from vehicle_photo where vehicle_id = ?",
                Integer.class, UUID.fromString(vehicleId))).isZero();
        assertThat(auditActions()).contains("VEHICLE_PHOTO_MALWARE_REJECTED");
    }

    @Test
    void photosCannotBeDeleted() {
        upload(owner, "auto.jpg", JPEG);

        assertThat(api.delete(photoPath(), owner.token()).status()).isEqualTo(405);
    }

    @Test
    void photoIsNotPartOfThePublicReport() {
        upload(owner, "auto.jpg", JPEG);
        ApiResponse share = api.post("/api/v1/vehicles/" + vehicleId + "/shares",
                Map.of("includeDocuments", true), owner.token());
        String token = share.field("token");

        ApiResponse report = api.get("/api/v1/public/vehicles/" + token);

        assertThat(report.status()).isEqualTo(200);
        assertThat(report.body().get("summary").get("documentCount").asInt()).isZero();
        assertThat(report.body().toString()).doesNotContain(sha256(JPEG));
    }

    // ---------- helpers ----------

    private String photoPath() {
        return "/api/v1/vehicles/" + vehicleId + "/photo";
    }

    private ApiResponse upload(Account actor, String fileName, byte[] content) {
        return api.postFile(photoPath(), Map.of(), fileName, "application/octet-stream", content, actor.token());
    }

    private List<String> auditActions() {
        return jdbcTemplate.queryForList(
                "select action from audit_event where entity_type = 'VEHICLE' and entity_id = ? order by sequence_number",
                String.class, UUID.fromString(vehicleId));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
