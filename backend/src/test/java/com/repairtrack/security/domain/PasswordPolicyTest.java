package com.repairtrack.security.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsReasonablePassword() {
        assertThatCode(() -> PasswordPolicy.validate("correct horse battery staple")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShortPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void rejectsBlankPasswordOfSufficientLength() {
        assertThatThrownBy(() -> PasswordPolicy.validate("              "))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void rejectsPasswordOver72BytesEvenWhenUnder72Characters() {
        // 30 characters, but each emoji is 4 bytes in UTF-8: 120 bytes
        String multiByte = "😀".repeat(30);

        assertThatThrownBy(() -> PasswordPolicy.validate(multiByte))
                .isInstanceOf(InvalidPasswordException.class);
    }
}
