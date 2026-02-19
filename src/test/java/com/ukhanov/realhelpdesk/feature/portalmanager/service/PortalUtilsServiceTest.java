package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
    // isValidUUIDFormat
    // ────────────────────────────────────────────────


    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "invalid"})
    @DisplayName("isValidUUIDFormat → невалидные/пустые входы → false")
    void isValidUUIDFormat_invalidOrEmptyInputs_returnFalse(String input) {
        assertThat(service.isValidUUIDFormat(input)).isFalse();
    }

    // ────────────────────────────────────────────────
    // validateUUIDList
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("validateUUIDList → валидный набор UUID → не бросает исключение")
    void validateUUIDList_validUuids_noException() {
        Set<UUID> validSet = Set.of(
                UUID.randomUUID(),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.randomUUID()
        );

        assertThatNoException()
                .isThrownBy(() -> service.validateUUIDList(validSet));
    }


    @Test
    @DisplayName("validateUUIDList → null → должен бросить NPE")
    void validateUUIDList_nullSet_throwsNPE() {
        assertThatThrownBy(() -> service.validateUUIDList(null))
                .isInstanceOf(NullPointerException.class);
    }

}