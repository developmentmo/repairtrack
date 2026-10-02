package com.repairtrack.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.document.DocumentType;
import com.repairtrack.document.application.DocumentViews.DocumentView;
import com.repairtrack.document.domain.Document;
import com.repairtrack.document.domain.EmptyFileException;
import com.repairtrack.document.domain.FileTooLargeException;
import com.repairtrack.document.domain.UnsupportedFileTypeException;
import com.repairtrack.document.infrastructure.DocumentRepository;
import com.repairtrack.document.infrastructure.DocumentStorage;
import com.repairtrack.document.infrastructure.StorageProperties;
import com.repairtrack.repair.RepairDocumentSupport;
import com.repairtrack.repair.RepairDocumentSupport.AttachmentTarget;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.verification.SourceType;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
    private static final byte[] PDF = "%PDF-1.4\n%test document\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private DocumentRepository documents;
    @Mock
    private DocumentStorage storage;
    @Mock
    private RepairDocumentSupport repairSupport;
    @Mock
    private ApplicationEventPublisher events;

    private DocumentService service;
    private final UUID repairId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(UUID.randomUUID(), "o@example.com", Set.of(Role.OWNER));

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties("http://localhost:3900", null, "garage", "GK", "s",
                "bucket", true, Duration.ofMinutes(5), DataSize.ofKilobytes(1));
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Amsterdam"));
        service = new DocumentService(documents, storage, properties, repairSupport, events, calendar);
        when(repairSupport.requireCanAttachDocument(owner, repairId))
                .thenReturn(new AttachmentTarget(repairId, vehicleId, SourceType.OWNER));
    }

    @Test
    void storesTheBytesAndRecordsTheirSha256() throws Exception {
        consumeStreamOnPut();

        DocumentView view = service.upload(owner, repairId, DocumentType.INVOICE, file("factuur.pdf", PDF));

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF));
        assertThat(view.sha256()).isEqualTo(expected);
        assertThat(view.mimeType()).isEqualTo("application/pdf");
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documents).save(saved.capture());
        assertThat(saved.getValue().getStorageKey())
                .isEqualTo("repair-events/" + repairId + "/" + saved.getValue().getId())
                .doesNotContain("factuur");
    }

    @Test
    void evidentiaryDocumentRaisesVerification() {
        consumeStreamOnPut();
        when(repairSupport.applyDocumentEvidence(eq(owner), eq(repairId), any())).thenReturn(true);

        DocumentView view = service.upload(owner, repairId, DocumentType.INVOICE, file("f.pdf", PDF));

        assertThat(view.repairVerificationRaised()).isTrue();
    }

    @Test
    void photoDoesNotRaiseVerification() {
        consumeStreamOnPut();

        DocumentView view = service.upload(owner, repairId, DocumentType.PHOTO, file("f.pdf", PDF));

        assertThat(view.repairVerificationRaised()).isFalse();
        verify(repairSupport, never()).applyDocumentEvidence(any(), any(), any());
    }

    @Test
    void unsupportedContentIsRejectedBeforeAnythingIsStored() {
        byte[] html = "<html>not a pdf</html>".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> service.upload(owner, repairId, DocumentType.INVOICE, file("invoice.pdf", html)))
                .isInstanceOf(UnsupportedFileTypeException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
        verify(documents, never()).save(any());
    }

    @Test
    void emptyAndOversizedFilesAreRejected() {
        assertThatThrownBy(() -> service.upload(owner, repairId, DocumentType.INVOICE, file("e.pdf", new byte[0])))
                .isInstanceOf(EmptyFileException.class);
        assertThatThrownBy(() -> service.upload(owner, repairId, DocumentType.INVOICE, file("big.pdf", new byte[2048])))
                .isInstanceOf(FileTooLargeException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    /** The real storage reads the stream (and thereby drives the digest); the mock must do the same. */
    private void consumeStreamOnPut() {
        doAnswer(invocation -> {
            ((InputStream) invocation.getArgument(1)).readAllBytes();
            return null;
        }).when(storage).put(anyString(), any(), anyLong(), anyString());
    }

    private static IncomingFile file(String name, byte[] content) {
        return new IncomingFile(name, content.length, new ByteArrayInputStream(content));
    }
}
