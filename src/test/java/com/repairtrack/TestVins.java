package com.repairtrack;

import java.util.concurrent.ThreadLocalRandom;

/** Random, syntactically valid VINs (ISO 3779 alphabet: no I, O, Q) for tests. */
public final class TestVins {

    private static final String ALPHABET = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";

    private TestVins() {
    }

    public static String random() {
        StringBuilder vin = new StringBuilder("WVWZZZ1K");
        for (int i = 0; i < 9; i++) {
            vin.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
        }
        return vin.toString();
    }
}
