package com.ukhanov.realhelpdesk.core.mail.model;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

@Component
public class EmailTemplates {

    public static final String DOMAIN = "front.example.ru";
    public static final String PROJECT_NAME = "real help desk";

    private final MessageSource messageSource;

    public EmailTemplates(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String registrationLinkSubject() {
        return text("email.registration.link.subject");
    }

    public String registrationLinkBody(String token) {
        return text("email.registration.link.body", PROJECT_NAME, DOMAIN, token);
    }

    public String registrationCodeSubject() {
        return text("email.registration.code.subject");
    }

    public String registrationCodeBody(String code) {
        return text("email.registration.code.body", PROJECT_NAME, DOMAIN, code);
    }

    public String passwordResetSubject() {
        return text("email.password.reset.subject");
    }

    public String passwordResetBody(String code) {
        return text("email.password.reset.body", PROJECT_NAME, DOMAIN, code);
    }

    public String ticketReplySubject(Long ticketId) {
        return text("email.ticket.reply.subject", String.valueOf(ticketId));
    }

    public String ticketReplyBody(Long ticketId, Long portalId) {
        return text("email.ticket.reply.body", String.valueOf(ticketId), DOMAIN, String.valueOf(portalId), PROJECT_NAME);
    }

    public String ticketCreatedSubject(Long ticketId) {
        return text("email.ticket.created.subject", String.valueOf(ticketId));
    }

    public String ticketCreatedBody(Long ticketId, Long portalId) {
        return text("email.ticket.created.body", String.valueOf(ticketId), DOMAIN, String.valueOf(portalId), PROJECT_NAME);
    }

    public String portalCreatedSubject(Long portalId) {
        return text("email.portal.created.subject", String.valueOf(portalId));
    }

    public String portalCreatedBody(Long portalId) {
        return text("email.portal.created.body", String.valueOf(portalId), DOMAIN, PROJECT_NAME);
    }

    public String portalAddUserSubject(Long portalId) {
        return text("email.portal.add.user.subject", String.valueOf(portalId));
    }

    public String portalAddUserBody(Long portalId) {
        return text("email.portal.add.user.body", DOMAIN, PROJECT_NAME);
    }

    public String updateStatusTicketSubject(Long ticketId, TicketStatus ticketStatus) {
        return text("email.ticket.status.subject", String.valueOf(ticketStatus), String.valueOf(ticketId));
    }

    public String updateStatusTicketBody(Long ticketId, Long portalId) {
        return text("email.ticket.status.body", DOMAIN, String.valueOf(portalId), String.valueOf(ticketId), PROJECT_NAME);
    }

    public String updatePriorityTicketSubject(Long ticketId, TicketPriority ticketPriority) {
        return text("email.ticket.priority.subject", String.valueOf(ticketPriority), String.valueOf(ticketId));
    }

    public String updatePriorityTicketBody(Long ticketId, Long portalId) {
        return text("email.ticket.priority.body", DOMAIN, String.valueOf(portalId), String.valueOf(ticketId), PROJECT_NAME);
    }

    public String deletedTicketSubject(Long ticketId) {
        return text("email.ticket.deleted.subject", String.valueOf(ticketId));
    }

    public String deletedTicketBody(Long ticketId, String email) {
        return text("email.ticket.deleted.body", String.valueOf(ticketId), email, PROJECT_NAME);
    }

    public String deletedPortalSubject(Long portalId) {
        return text("email.portal.deleted.subject", String.valueOf(portalId));
    }

    public String deletedPortalBody(Long portalId, String email) {
        return text("email.portal.deleted.body", String.valueOf(portalId), email, PROJECT_NAME);
    }

    private String text(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }
}
