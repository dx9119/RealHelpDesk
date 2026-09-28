package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Тесты утилитного сервиса PortalUtilsService")
class PortalUtilsServiceTest {

    private PortalUtilsService service;

    @BeforeEach
    void setUp() {
        service = new PortalUtilsService();
    }

    // ────────────────────────────────────────────────
    // isValidUserId
    // ────────────────────────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "invalid", "-1", "0"})
    @DisplayName("isValidUserId → невалидные/пустые/не положительные входы → false")
    void isValidUserId_invalidInputs_returnFalse(String input) {
        assertThat(service.isValidUserId(input)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "42", "9223372036854775807"})
    @DisplayName("isValidUserId → положительное число → true")
    void isValidUserId_validIds_returnTrue(String input) {
        assertThat(service.isValidUserId(input)).isTrue();
    }

    // ────────────────────────────────────────────────
    // validateIdList
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("validateIdList → валидный набор id → не бросает исключение")
    void validateIdList_validIds_noException() {
        Set<Long> validSet = Set.of(1L, 42L, 9223372036854775807L);

        assertThatNoException()
                .isThrownBy(() -> service.validateIdList(validSet));
    }

    @Test
    @DisplayName("validateIdList → null → должен бросить NPE")
    void validateIdList_nullSet_throwsNPE() {
        assertThatThrownBy(() -> service.validateIdList(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("validateIdList → id <= 0 → PortalException")
    void validateIdList_nonPositiveId_throwsPortalException() {
        Set<Long> invalidSet = new HashSet<>(List.of(1L, -5L));

        assertThatThrownBy(() -> service.validateIdList(invalidSet))
                .isInstanceOf(PortalException.class);
    }
}
