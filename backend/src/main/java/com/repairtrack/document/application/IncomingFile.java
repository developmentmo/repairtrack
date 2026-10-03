package com.repairtrack.document.application;

import java.io.IOException;
import java.io.InputStream;

/**
 * An upload as received by the API layer; {@code fileName} and the bytes are untrusted. The content can be
 * opened more than once (type detection and malware scan before storing), e.g. from the spooled multipart file.
 */
public record IncomingFile(String fileName, long size, ContentSource content) {

    @FunctionalInterface
    public interface ContentSource {

        /** A fresh stream over the complete content; the caller closes it. */
        InputStream open() throws IOException;
    }
}
