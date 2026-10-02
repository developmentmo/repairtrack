package com.repairtrack.document.application;

import java.io.InputStream;

/** An upload as received by the API layer; {@code fileName} and the bytes are untrusted. */
public record IncomingFile(String fileName, long size, InputStream content) {
}
