package com.repairtrack.document.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.repairtrack.document.DocumentType;
import com.repairtrack.document.application.DocumentService;
import com.repairtrack.document.application.DocumentViews.DocumentView;
import com.repairtrack.document.application.DocumentViews.DownloadView;
import com.repairtrack.document.application.DocumentViews.IntegrityView;
import com.repairtrack.document.application.IncomingFile;
import com.repairtrack.security.AuthenticatedUser;

/**
 * Documents. Upload is {@code multipart/form-data} with a {@code file} part and a {@code documentType}
 * field. There is no update or delete endpoint: attached documents are part of the history.
 */
@RestController
class DocumentController {

    private final DocumentService documentService;

    DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(path = "/api/v1/repairs/{repairId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse upload(@AuthenticationPrincipal AuthenticatedUser actor,
                            @PathVariable("repairId") UUID repairId,
                            @RequestParam("documentType") DocumentType documentType,
                            @RequestPart("file") MultipartFile file) throws IOException {
        try (InputStream content = file.getInputStream()) {
            var incoming = new IncomingFile(file.getOriginalFilename(), file.getSize(), content);
            return DocumentResponse.from(documentService.upload(actor, repairId, documentType, incoming));
        }
    }

    @GetMapping("/api/v1/repairs/{repairId}/documents")
    List<DocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable("repairId") UUID repairId) {
        return documentService.listForRepair(actor, repairId).stream().map(DocumentResponse::from).toList();
    }

    /** Metadata plus a presigned download URL valid for a few minutes. */
    @GetMapping("/api/v1/documents/{documentId}")
    DocumentResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                         @PathVariable("documentId") UUID documentId) {
        return DocumentResponse.from(documentService.download(actor, documentId));
    }

    @GetMapping("/api/v1/documents/{documentId}/integrity")
    IntegrityResponse integrity(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable("documentId") UUID documentId) {
        IntegrityView view = documentService.checkIntegrity(actor, documentId);
        return new IntegrityResponse(view.documentId(), view.expectedSha256(), view.actualSha256(), view.intact(),
                view.checkedAt());
    }

    /**
     * {@code downloadUrl}/{@code downloadUrlExpiresAt} only on {@code GET /documents/{id}};
     * {@code repairVerificationRaised} only meaningful on upload.
     */
    public record DocumentResponse(UUID id, UUID repairEventId, DocumentType documentType, String fileName,
                                   String mimeType, long fileSize, String sha256, Instant uploadedAt,
                                   boolean repairVerificationRaised, String downloadUrl, Instant downloadUrlExpiresAt) {

        static DocumentResponse from(DocumentView v) {
            return new DocumentResponse(v.id(), v.repairEventId(), v.documentType(), v.fileName(), v.mimeType(),
                    v.fileSize(), v.sha256(), v.uploadedAt(), v.repairVerificationRaised(), null, null);
        }

        static DocumentResponse from(DownloadView d) {
            DocumentView v = d.document();
            return new DocumentResponse(v.id(), v.repairEventId(), v.documentType(), v.fileName(), v.mimeType(),
                    v.fileSize(), v.sha256(), v.uploadedAt(), false, d.downloadUrl().toString(), d.expiresAt());
        }
    }

    public record IntegrityResponse(UUID documentId, String expectedSha256, String actualSha256, boolean intact,
                                    Instant checkedAt) {
    }
}
