package com.repairtrack.document.domain;

import java.util.Optional;
import java.util.Set;

/** File types we accept, identified by content (magic bytes), never by name or client-sent content type. */
public enum DetectedFileType {

    PDF("application/pdf", "pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D}),                     // %PDF-
    JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    /** RIFF container: "RIFF", four size bytes, then "WEBP". */
    WEBP("image/webp", "webp", new byte[] {0x52, 0x49, 0x46, 0x46}, 8, new byte[] {0x57, 0x45, 0x42, 0x50});

    /** Accepted for history documents and dispute evidence. */
    public static final Set<DetectedFileType> DOCUMENTS = Set.of(PDF, JPEG, PNG);

    /** Accepted for vehicle photos. */
    public static final Set<DetectedFileType> PHOTOS = Set.of(JPEG, PNG, WEBP);

    /** Enough bytes to recognise every supported type. */
    public static final int HEADER_LENGTH = 12;

    private final String mimeType;
    private final String extension;
    private final byte[] signature;
    private final int secondSignatureOffset;
    private final byte[] secondSignature;

    DetectedFileType(String mimeType, String extension, byte[] signature) {
        this(mimeType, extension, signature, 0, new byte[0]);
    }

    DetectedFileType(String mimeType, String extension, byte[] signature, int secondSignatureOffset,
                     byte[] secondSignature) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.signature = signature;
        this.secondSignatureOffset = secondSignatureOffset;
        this.secondSignature = secondSignature;
    }

    public static Optional<DetectedFileType> detect(byte[] header) {
        for (DetectedFileType type : values()) {
            if (type.matches(header)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static Optional<DetectedFileType> fromMimeType(String mimeType) {
        for (DetectedFileType type : values()) {
            if (type.mimeType.equals(mimeType)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private boolean matches(byte[] header) {
        return matchesAt(header, 0, signature) && matchesAt(header, secondSignatureOffset, secondSignature);
    }

    private static boolean matchesAt(byte[] header, int offset, byte[] expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }
}
