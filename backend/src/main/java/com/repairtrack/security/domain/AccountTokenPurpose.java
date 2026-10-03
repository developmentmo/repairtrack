package com.repairtrack.security.domain;

import java.time.Duration;

public enum AccountTokenPurpose {

    EMAIL_VERIFICATION(Duration.ofHours(24)),
    PASSWORD_RESET(Duration.ofHours(1));

    private final Duration validity;

    AccountTokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}
