package com.repairtrack.dispute.application;

import com.repairtrack.document.EvidenceFiles;

/** A file as received by the API; the name and the bytes are untrusted (checked by the document module). */
public record EvidenceUpload(String fileName, long size, EvidenceFiles.Content content) {
}
