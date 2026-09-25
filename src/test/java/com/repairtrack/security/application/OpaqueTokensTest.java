package com.repairtrack.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpaqueTokensTest {

    @Test
    void generatesUrlSafeUniqueTokensWith256Bits() {
        String a = OpaqueTokens.generate();
        String b = OpaqueTokens.generate();

        assertThat(a).isNotEqualTo(b).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void hashIsDeterministicHexSha256() {
        assertThat(OpaqueTokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
