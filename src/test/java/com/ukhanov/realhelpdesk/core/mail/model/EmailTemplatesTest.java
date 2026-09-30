package com.ukhanov.realhelpdesk.core.mail.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Шаблоны писем: EmailTemplates")
class EmailTemplatesTest {

    private static final Long TICKET_ID = 77L;
    private static final Long PORTAL_ID = 5L;

    @Test
    @DisplayName("Подтверждение регистрации: тема и код в теле письма")
    void registrationLink_containsTokenAndConfirmUrl() {
        assertThat(EmailTemplates.registrationLinkSubject()).isEqualTo("Подтверждение регистрации");

        String body = EmailTemplates.registrationLinkBody("123456");
        assertThat(body).contains("123456").contains("https://" + EmailTemplates.DOMAIN + "/notify-settings")
                .contains(EmailTemplates.PROJECT_NAME);
    }

    @Test
    @DisplayName("Код подтверждения: тема и код в теле письма")
    void registrationCode_containsCodeAndConfirmUrl() {
        assertThat(EmailTemplates.registrationCodeSubject()).isEqualTo("Код подтверждения");

        String body = EmailTemplates.registrationCodeBody("654321");
        assertThat(body).contains("654321").contains("https://" + EmailTemplates.DOMAIN + "/notify-settings");
    }

    @Test
    @DisplayName("Сброс пароля: ссылка на форму со сбросом содержит код")
    void passwordReset_containsResetLinkWithCode() {
        assertThat(EmailTemplates.passwordResetSubject()).isEqualTo("Сброс пароля");

        String body = EmailTemplates.passwordResetBody("999888");
        assertThat(body).contains("https://" + EmailTemplates.DOMAIN + "/pass-reset?code=999888").contains("проигнорируйте это письмо");
    }

    @Test
    @DisplayName("Новое сообщение в заявке: тема и ссылка на заявку")
    void ticketReply_containsSubjectAndTicketLink() {
        assertThat(EmailTemplates.ticketReplySubject(TICKET_ID)).isEqualTo("[Заявка#77] Новое сообщение");

        String body = EmailTemplates.ticketReplyBody(TICKET_ID, PORTAL_ID);
        assertThat(body).contains("В заявке #77 появилось новое сообщение").contains(ticketLink());
    }

    @Test
    @DisplayName("Созданная заявка: тема и ссылка на заявку")
    void ticketCreated_containsSubjectAndTicketLink() {
        assertThat(EmailTemplates.ticketCreatedSubject(TICKET_ID)).isEqualTo("Создана новая заявка #77");

        String body = EmailTemplates.ticketCreatedBody(TICKET_ID, PORTAL_ID);
        assertThat(body).contains("Cоздана заявка #77").contains(ticketLink());
    }

    @Test
    @DisplayName("Ссылки на заявку одинаковы во всех шаблонах: /portal/{portalId}/ticket/{ticketId}")
    void ticketLinks_areConsistentAcrossTemplates() {
        String link = ticketLink();

        assertThat(EmailTemplates.ticketCreatedBody(TICKET_ID, PORTAL_ID)).contains(link);
        assertThat(EmailTemplates.ticketReplyBody(TICKET_ID, PORTAL_ID)).contains(link);
        assertThat(EmailTemplates.updateStatusTicketBody(TICKET_ID, PORTAL_ID)).contains(link);
        assertThat(EmailTemplates.updatePriorityTicketBody(TICKET_ID, PORTAL_ID)).contains(link);
    }

    private String ticketLink() {
        return "https://" + EmailTemplates.DOMAIN + "/ticket-show/portal/" + PORTAL_ID + "/ticket/" + TICKET_ID;
    }

    @Test
    @DisplayName("Созданный портал: тема и ссылка на список порталов")
    void portalCreated_containsSubjectAndPortalUrl() {
        assertThat(EmailTemplates.portalCreatedSubject(PORTAL_ID)).isEqualTo("Создан новый портал #5");

        assertThat(EmailTemplates.portalCreatedBody(PORTAL_ID)).contains("Cоздан портал #5")
                .contains("https://" + EmailTemplates.DOMAIN + "/portal-manager");
    }

    @Test
    @DisplayName("Добавленный пользователь портала: тема и ссылка на общий список порталов")
    void portalAddUser_containsSubjectAndPortalUrl() {
        assertThat(EmailTemplates.portalAddUserSubject(PORTAL_ID)).isEqualTo("Вас добавили к порталу #5");

        assertThat(EmailTemplates.portalAddUserBody(PORTAL_ID)).contains("Общие порталы")
                .contains("https://" + EmailTemplates.DOMAIN + "/portal-manager");
    }

    @Test
    @DisplayName("Смена статуса заявки: тема со статусом, ссылка ведёт на портал и заявку")
    void updateStatusTicket_containsStatusAndPortalScopedLink() {
        assertThat(EmailTemplates.updateStatusTicketSubject(TICKET_ID, TicketStatus.CLOSED)).isEqualTo("Задан статус:CLOSED для заявки#77");

        assertThat(EmailTemplates.updateStatusTicketBody(TICKET_ID, PORTAL_ID))
                .contains("https://" + EmailTemplates.DOMAIN + "/ticket-show/portal/" + PORTAL_ID + "/ticket/" + TICKET_ID);
    }

    @Test
    @DisplayName("Смена приоритета заявки: тема с приоритетом, ссылка ведёт на портал и заявку")
    void updatePriorityTicket_containsPriorityAndPortalScopedLink() {
        assertThat(EmailTemplates.updatePriorityTicketSubject(TICKET_ID, TicketPriority.CRITICAL))
                .isEqualTo("Задан приоритет:CRITICAL для заявки#77");

        assertThat(EmailTemplates.updatePriorityTicketBody(TICKET_ID, PORTAL_ID))
                .contains("https://" + EmailTemplates.DOMAIN + "/ticket-show/portal/" + PORTAL_ID + "/ticket/" + TICKET_ID);
    }

    @Test
    @DisplayName("Удалённая заявка: тема и инициатор удаления в теле")
    void deletedTicket_containsSubjectAndActorEmail() {
        assertThat(EmailTemplates.deletedTicketSubject(TICKET_ID)).isEqualTo("Удалена заявка#77");

        assertThat(EmailTemplates.deletedTicketBody(TICKET_ID, "user@example.com"))
                .contains("Заявка #77 была удалена пользователем user@example.com");
    }

    @Test
    @DisplayName("Удалённый портал: тема и инициатор удаления в теле")
    void deletedPortal_containsSubjectAndActorEmail() {
        assertThat(EmailTemplates.deletedPortalSubject(PORTAL_ID)).isEqualTo("Удален портал#5");

        assertThat(EmailTemplates.deletedPortalBody(PORTAL_ID, "user@example.com"))
                .contains("Портал #5 был удален пользователем user@example.com");
    }
}
