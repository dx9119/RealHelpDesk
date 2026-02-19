package com.ukhanov.realhelpdesk.domain.portal.service;

import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalRepository;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тесты сервиса PortalDomainService")
class PortalDomainServiceTest {

    @Mock
    private PortalRepository mockPortalRepository;

    private PortalDomainService service;

    private static final UUID OWNER_ID = UUID.fromString("a99e4ebb-e9cc-48a0-8648-f6a4de67a293");
    private static final UUID USER_ID  = UUID.fromString("dab77ff3-6ec8-48e9-870d-b83e559a302c");
    private static final Long PORTAL_ID = 42L;

    @BeforeEach
    void setUp() {
        service = new PortalDomainService(mockPortalRepository);
    }

    // ────────────────────────────────────────────────
    // getPortalsByOwnerId
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("getPortalsByOwnerId → возвращает отсортированный список порталов владельца")
    void getPortalsByOwnerId_shouldReturnSortedList() {
        PortalModel p1 = createPortal(100L, "Portal A");
        PortalModel p2 = createPortal(50L,  "Portal B");

        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(p1, p2));

        List<PortalModel> result = service.getPortalsByOwnerId(OWNER_ID);

        assertThat(result)
                .hasSize(2)
                .extracting(PortalModel::getName)
                .containsExactly("Portal A", "Portal B");

        verify(mockPortalRepository).findAllByOwnerIdOrderByCreatedAtDesc(OWNER_ID);
    }

    @Test
    @DisplayName("getPortalsByOwnerId → пустой список когда ничего не найдено")
    void getPortalsByOwnerId_emptyResult() {
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(Collections.emptyList());

        List<PortalModel> result = service.getPortalsByOwnerId(OWNER_ID);

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("getPortalsByOwnerId → кидает NPE при null ownerId")
    void getPortalsByOwnerId_nullOwnerId_shouldThrowNPE(String dummy) {
        assertThatThrownBy(() -> service.getPortalsByOwnerId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ownerId не должен быть null");
    }

    // ────────────────────────────────────────────────
    // getPortalById
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("getPortalById → успешно возвращает найденный портал")
    void getPortalById_found() throws PortalException {
        PortalModel expected = createPortal(PORTAL_ID, "Test Portal");
        when(mockPortalRepository.findById(PORTAL_ID)).thenReturn(Optional.of(expected));

        PortalModel result = service.getPortalById(PORTAL_ID);

        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("getPortalById → кидает PortalException если портал не найден")
    void getPortalById_notFound() {
        when(mockPortalRepository.findById(PORTAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPortalById(PORTAL_ID))
                .isInstanceOf(PortalException.class)
                .hasMessageContaining("Портал с ID " + PORTAL_ID + " не найден");
    }

    @Test
    @DisplayName("getPortalById → проверка на null portalId")
    void getPortalById_nullId() {
        assertThatThrownBy(() -> service.getPortalById(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("portalId must not be null");
    }

    // ────────────────────────────────────────────────
    // savePortal
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("savePortal → сохраняет и возвращает портал")
    void savePortal_success() {
        PortalModel toSave = createPortal(null, "New Portal");
        PortalModel saved = createPortal(777L, "New Portal");

        when(mockPortalRepository.save(any(PortalModel.class))).thenReturn(saved);

        PortalModel result = service.savePortal(toSave);

        assertThat(result).usingRecursiveComparison().isEqualTo(saved);
        verify(mockPortalRepository).save(toSave);
    }

    @Test
    @DisplayName("savePortal → кидает NPE при null")
    void savePortal_null() {
        assertThatThrownBy(() -> service.savePortal(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("portal не должен быть null");
    }

    // ────────────────────────────────────────────────
    // isPortalExistByName
    // ────────────────────────────────────────────────

    @ParameterizedTest(name = "Имя \"{0}\" → существует = {1}")
    @CsvSource({
            "'',          false",
            "' ',         false",
            "Portal One,  true",
            "Портал №2,   true"
    })
    void isPortalExistByName_variousNames(String name, boolean exists) {
        when(mockPortalRepository.existsByOwnerIdAndNameAndIsDeletedFalse(OWNER_ID, name))
                .thenReturn(exists);

        boolean result = service.isPortalExistByName(OWNER_ID, name);

        assertThat(result).isEqualTo(exists);
    }

    @Test
    void isPortalExistByName_nullName() {
        assertThatThrownBy(() -> service.isPortalExistByName(OWNER_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("portalName не должен быть null");
    }

    // ────────────────────────────────────────────────
    // deletePortalById
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("deletePortalById → вызывает удаление и возвращает true")
    void deletePortalById_success() {
        boolean result = service.deletePortalById(PORTAL_ID);

        assertThat(result).isTrue();
        verify(mockPortalRepository).deleteById(PORTAL_ID);
    }

    @Test
    @DisplayName("deletePortalById → null id → NPE")
    void deletePortalById_null() {
        assertThatThrownBy(() -> service.deletePortalById(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("portalId не должен быть null");
    }

    // ────────────────────────────────────────────────
    // Вспомогательные методы
    // ────────────────────────────────────────────────

    private static PortalModel createPortal(Long id, String name) {
        PortalModel p = new PortalModel();
        p.setId(id);
        p.setName(name);
        p.setCreatedAt(Instant.now());
        p.setDeleted(false);
        p.setPublic(false);
        return p;
    }
}