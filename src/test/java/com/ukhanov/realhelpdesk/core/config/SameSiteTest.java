package com.ukhanov.realhelpdesk.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Enum SameSite: весь набор значений для auth-cookie")
class SameSiteTest {

    @Test
    @DisplayName("В наборе ровно три элемента: None, Lax, Strict")
    void containsExactlyThreeElements() {
        assertThat(SameSite.values()).extracting(SameSite::name).containsExactly("NONE", "LAX", "STRICT");
    }

    @Test
    @DisplayName("В Set-Cookie уходит форма из getValue: None/Lax/Strict")
    void wireValuesMatchCookieHeader() {
        assertThat(SameSite.NONE.getValue()).isEqualTo("None");
        assertThat(SameSite.LAX.getValue()).isEqualTo("Lax");
        assertThat(SameSite.STRICT.getValue()).isEqualTo("Strict");
    }
}
