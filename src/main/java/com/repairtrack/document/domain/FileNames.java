package com.repairtrack.document.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Makes a client-supplied file name safe to store and to put in a Content-Disposition header.
 * The name is display metadata only; storage keys never contain it.
 */
public final class FileNames {

    private static final int MAX_LENGTH = 200;

    private FileNames() {
    }

    public static String sanitize(String original, DetectedFileType type) {
        String name = original == null ? "" : original;
        // keep only the last path segment (some clients send full paths)
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC)
                .replaceAll("[\\p{Cntrl}\"<>|:*?]", "")
                .strip();
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            name = "document";
        }
        String expectedExtension = "." + type.extension();
        if (!name.toLowerCase(Locale.ROOT).endsWith(expectedExtension)
                && !(type == DetectedFileType.JPEG && name.toLowerCase(Locale.ROOT).endsWith(".jpeg"))) {
            name = name + expectedExtension;
        }
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH - expectedExtension.length()) + expectedExtension;
        }
        return name;
    }

    /** ASCII-only fallback for the plain {@code filename=} parameter of Content-Disposition. */
    public static String asciiFallback(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("[^\\x20-\\x7E]", "");
        ascii = ascii.replace("\\", "").replace("\"", "");
        return ascii.isBlank() ? "document" : ascii;
    }
}
