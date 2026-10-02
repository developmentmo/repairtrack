package com.repairtrack.document.domain;

import java.util.Optional;

/** File types we accept, identified by content (magic bytes), never by name or client-sent content type. */
public enum DetectedFileType {

    PDF("application/pdf", "pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D}),                     // %PDF-
    JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

    /** Enough bytes to recognise every supported type. */
    public static final int HEADER_LENGTH = 8;

    private final String mimeType;
    private final String extension;
    private final byte[] signature;

    DetectedFileType(String mimeType, String extension, byte[] signature) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.signature = signature;
    }

    public static Optional<DetectedFileType> detect(byte[] header) {
        for (DetectedFileType type : values()) {
            if (type.matches(header)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    private boolean matches(byte[] header) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
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
