package com.chat.server.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileNamesTest {

    @Test
    void keepsNormalName() {
        assertThat(FileNames.sanitize("report.pdf")).isEqualTo("report.pdf");
    }

    @Test
    void replacesNullAndBlankWithDefault() {
        assertThat(FileNames.sanitize(null)).isEqualTo("file");
        assertThat(FileNames.sanitize("   ")).isEqualTo("file");
    }

    @Test
    void stripsHeaderBreakingCharacters() {
        assertThat(FileNames.sanitize("a\r\nb")).isEqualTo("a__b");
        assertThat(FileNames.sanitize("evil\".txt")).isEqualTo("evil_.txt");
    }

    @Test
    void stripsPathSeparators() {
        assertThat(FileNames.sanitize("../../etc/passwd")).isEqualTo(".._.._etc_passwd");
        assertThat(FileNames.sanitize("dir\\file.txt")).isEqualTo("dir_file.txt");
    }

    @Test
    void truncatesOverlyLongNames() {
        String longName = "a".repeat(300);
        assertThat(FileNames.sanitize(longName)).hasSize(200);
    }
}
