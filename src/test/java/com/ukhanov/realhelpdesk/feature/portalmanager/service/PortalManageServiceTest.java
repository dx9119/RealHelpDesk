package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.mail.support.EmailTemplatesFixture;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationAdapter;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
import com.ukhanov.realhelpdesk.core.security.limiter.exception.LimitException;
import com.ukhanov.realhelpdesk.core.security.limiter.service.LimitService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.feature.notificationmanager.service.NotificationPublisher;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.DeleteResult;
import com.ukhanov.realhelpdesk.feature.usermanager.exception.UserManageException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Письма при работе с порталами: PortalManageService")
class PortalManageServiceTest {

    private static final Long OWNER_ID = 7L;
    private static final Long PORTAL_ID = 100L;
    private static final String OWNER_EMAIL = "owner@example.com";

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PortalDomainService portalDomainService;

    @Mock
    private PaginationAdapter paginationAdapter;

    @Mock
    private PortalUtilsService portalUtilsService;

    @Mock
    private AccessValidationService accessValidationService;

    @Mock
    private LimitService limitService;

    @Mock
    private UserDomainService userDomainService;

    @Mock
    private EmailDeliveryService emailDeliveryService;

    @Mock
    private NotificationPublisher notificationPublisher;

    private PortalManageService service;

    private final EmailTemplates emailTemplates = EmailTemplatesFixture.emailTemplates();

    @BeforeEach
    void setUp() {
        service = new PortalManageService(currentUserProvider, portalDomainService, paginationAdapter, portalUtilsService,
                accessValidationService, limitService, userDomainService, emailDeliveryService, emailTemplates, notificationPublisher);

        UserModel owner = new UserModel();
        owner.setId(OWNER_ID);
        owner.setEmail(OWNER_EMAIL);
        owner.setEmailVerified(true);
        lenient().when(currentUserProvider.getCurrentUserModel()).thenReturn(owner);
        lenient().when(limitService.hasUserReachedPortalLimit(owner)).thenReturn(false);
    }

    // ────────────────────────────────────────────────
    // createPortal
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Создание портала → письмо NEW_PORTAL участникам")
    void createPortal_sendsPortalCreatedEmail() throws Exception {
        when(portalDomainService.isPortalExistByName(OWNER_ID, "Портал")).thenReturn(false);
        when(portalDomainService.savePortal(any())).thenAnswer(invocation -> {
            PortalModel portal = invocation.getArgument(0);
            portal.setId(PORTAL_ID);
            return portal;
        });

        CreatePortalResponse response = service.createPortal(request("Портал"));

        assertThat(response.getId()).isEqualTo(PORTAL_ID);
        verify(emailDeliveryService).initNotifyPortalUsers(any(PortalModel.class), eq(emailTemplates.portalCreatedSubject(PORTAL_ID)),
                eq(emailTemplates.portalCreatedBody(PORTAL_ID)), eq(NotificationEvent.NEW_PORTAL));
    }

    @Test
    @DisplayName("Неподтверждённая почта: портал не создаётся, письма нет")
    void createPortal_emailNotVerified_throwsWithoutEmail() {
        currentUserProvider.getCurrentUserModel().setEmailVerified(false);

        assertThatThrownBy(() -> service.createPortal(request("Портал"))).isInstanceOf(UserManageException.class)
                .hasMessageContaining("Подтвердите ваш адрес электронной почты");

        verifyNoInteractions(emailDeliveryService, portalDomainService);
    }

    @Test
    @DisplayName("Достигнут лимит порталов: ошибка и письма нет")
    void createPortal_limitReached_throwsWithoutEmail() {
        when(limitService.hasUserReachedPortalLimit(currentUserProvider.getCurrentUserModel())).thenReturn(true);

        assertThatThrownBy(() -> service.createPortal(request("Портал"))).isInstanceOf(LimitException.class);

        verifyNoInteractions(emailDeliveryService);
    }

    // ────────────────────────────────────────────────
    // deletePortals
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Удаление портала владельцем → письмо PORTAL_DELETED")
    void deletePortals_owner_sendsPortalDeletedEmail() throws Exception {
        PortalModel portal = portal(PORTAL_ID);
        when(accessValidationService.hasPortalOwner(PORTAL_ID)).thenReturn(true);
        when(portalDomainService.getPortalById(PORTAL_ID)).thenReturn(portal);
        when(portalDomainService.savePortal(any())).thenReturn(portal);

        DeleteResult result = service.deletePortals(Set.of(PORTAL_ID));

        assertThat(result.getCount()).isEqualTo(1);
        verify(emailDeliveryService).initNotifyPortalUsers(eq(portal), eq(emailTemplates.deletedPortalSubject(PORTAL_ID)),
                eq(emailTemplates.deletedPortalBody(PORTAL_ID, OWNER_EMAIL)), eq(NotificationEvent.PORTAL_DELETED));
    }

    @Test
    @DisplayName("Удаление без прав владельца: письмо не уходит")
    void deletePortals_notOwner_sendsNoEmail() throws Exception {
        when(accessValidationService.hasPortalOwner(PORTAL_ID)).thenReturn(false);

        DeleteResult result = service.deletePortals(Set.of(PORTAL_ID));

        assertThat(result.getCount()).isZero();
        verifyNoInteractions(emailDeliveryService);
    }

    // ────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────

    private CreatePortalRequest request(String name) {
        CreatePortalRequest request = new CreatePortalRequest();
        request.setName(name);
        return request;
    }

    private PortalModel portal(Long id) {
        PortalModel portal = new PortalModel();
        portal.setId(id);
        portal.setName("Портал");
        portal.setOwner(currentUserProvider.getCurrentUserModel());
        return portal;
    }
}
