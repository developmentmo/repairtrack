package com.repairtrack.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DetectedFileTypeTest {

    @Test
    void recognisesSupportedTypesByContent() {
        assertThat(DetectedFileType.detect("%PDF-1.7 rest".getBytes(StandardCharsets.US_ASCII))).contains(DetectedFileType.PDF);
        assertThat(DetectedFileType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0}))
                .contains(DetectedFileType.JPEG);
        assertThat(DetectedFileType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains(DetectedFileType.PNG);
        assertThat(DetectedFileType.detect("RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)))
                .contains(DetectedFileType.WEBP);
    }

    @Test
    void otherRiffContainersAreNotWebp() {
        assertThat(DetectedFileType.detect("RIFF$\u0000\u0000\u0000WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1)))
                .isEmpty();
        assertThat(DetectedFileType.detect("RIFF$\u0000\u0000\u0000WE".getBytes(StandardCharsets.ISO_8859_1)))
                .isEmpty(); // truncated
    }

    @Test
    void documentsAndPhotosAcceptDifferentTypes() {
        assertThat(DetectedFileType.DOCUMENTS).containsExactlyInAnyOrder(DetectedFileType.PDF, DetectedFileType.JPEG,
                DetectedFileType.PNG);
        assertThat(DetectedFileType.PHOTOS).containsExactlyInAnyOrder(DetectedFileType.JPEG, DetectedFileType.PNG,
                DetectedFileType.WEBP);
    }

    @Test
    void rejectsEverythingElseRegardlessOfName() {
        assertThat(DetectedFileType.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(DetectedFileType.detect("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1))).isEmpty(); // .exe
        assertThat(DetectedFileType.detect(new byte[] {0x25, 0x50})).isEmpty(); // truncated %P
        assertThat(DetectedFileType.detect(new byte[0])).isEmpty();
    }
}
