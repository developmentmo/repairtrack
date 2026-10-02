package com.repairtrack.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileNamesTest {

    @Test
    void stripsPathsAndDangerousCharacters() {
        assertThat(FileNames.sanitize("../../etc/passwd.pdf", DetectedFileType.PDF)).isEqualTo("passwd.pdf");
        assertThat(FileNames.sanitize("C:\\Users\\jan\\factuur.pdf", DetectedFileType.PDF)).isEqualTo("factuur.pdf");
        assertThat(FileNames.sanitize("in\"voice<1>.pdf", DetectedFileType.PDF)).isEqualTo("invoice1.pdf");
    }

    @Test
    void extensionFollowsTheDetectedContent() {
        assertThat(FileNames.sanitize("photo.pdf", DetectedFileType.PNG)).isEqualTo("photo.pdf.png");
        assertThat(FileNames.sanitize("photo.JPEG", DetectedFileType.JPEG)).isEqualTo("photo.JPEG");
        assertThat(FileNames.sanitize(null, DetectedFileType.PDF)).isEqualTo("document.pdf");
        assertThat(FileNames.sanitize("   ", DetectedFileType.JPEG)).isEqualTo("document.jpg");
    }

    @Test
    void longNamesAreTruncatedKeepingTheExtension() {
        String name = FileNames.sanitize("a".repeat(500) + ".pdf", DetectedFileType.PDF);

        assertThat(name).hasSize(200).endsWith(".pdf");
    }

    @Test
    void asciiFallbackRemovesNonAsciiAndQuotes() {
        assertThat(FileNames.asciiFallback("factuur-é\"x\".pdf")).isEqualTo("factuur-ex.pdf");
    }
}
