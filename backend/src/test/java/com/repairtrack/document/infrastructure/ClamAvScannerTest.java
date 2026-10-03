package com.repairtrack.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The clamd INSTREAM protocol against an in-process fake clamd. */
class ClamAvScannerTest {

    private ServerSocket server;
    private ClamAvScanner scanner;

    @BeforeEach
    void setUp() throws IOException {
        server = new ServerSocket(0);
        scanner = new ClamAvScanner(new MalwareScanProperties("clamav", "localhost", server.getLocalPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void sendsTheContentInLengthPrefixedChunksAndReadsACleanVerdict() throws Exception {
        byte[] content = new byte[ClamAvScanner.CHUNK_SIZE + 10]; // two chunks
        CompletableFuture<byte[]> received = fakeClamd("stream: OK\0");

        MalwareScanner.ScanResult result = scanner.scan(new ByteArrayInputStream(content));

        assertThat(result.clean()).isTrue();
        assertThat(received.get(5, TimeUnit.SECONDS)).hasSize(content.length);
    }

    @Test
    void reportsTheSignatureOfInfectedContent() throws Exception {
        fakeClamd("stream: Win.Test.EICAR_HDB-1 FOUND\0");

        MalwareScanner.ScanResult result = scanner.scan(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertThat(result.clean()).isFalse();
        assertThat(result.signature()).isEqualTo("Win.Test.EICAR_HDB-1");
    }

    @Test
    void failsClosedOnErrorsAndWhenClamdIsUnreachable() throws Exception {
        fakeClamd("INSTREAM size limit exceeded. ERROR\0");
        assertThatThrownBy(() -> scanner.scan(new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(MalwareScannerUnavailableException.class);

        server.close();
        assertThatThrownBy(() -> scanner.scan(new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(MalwareScannerUnavailableException.class);
        assertThat(scanner.ping()).isFalse();
    }

    /** Accepts one INSTREAM connection, collects the streamed bytes and answers {@code reply}. */
    private CompletableFuture<byte[]> fakeClamd(String reply) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = server.accept()) {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                byte[] command = new byte["zINSTREAM\0".length()];
                in.readFully(command);
                assertThat(new String(command, StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");
                ByteArrayOutputStream received = new ByteArrayOutputStream();
                int length;
                while ((length = in.readInt()) > 0) {
                    received.write(in.readNBytes(length));
                }
                socket.getOutputStream().write(reply.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                return received.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
