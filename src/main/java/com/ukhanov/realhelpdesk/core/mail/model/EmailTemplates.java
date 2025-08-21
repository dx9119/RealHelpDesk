package com.ukhanov.realhelpdesk.core.mail.model;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

public class EmailTemplates {

    public static final String DOMAIN = "front.zayavus.ru";
    public static final String PROJECT_NAME = "Заявус";

    public static String registrationLinkSubject() {
        return "Подтверждение регистрации";
    }

    public static String registrationLinkBody(String token) {
        return String.format("""
            Добрый день!

            Для завершения регистрации в системе «%s», пожалуйста, подтвердите ваш адрес электронной почты, перейдите по ссылке:
            https://%s/notify-settings
            
            В поле "✅ Подтверждение Email", введите данный код:
            %s

            С уважением,  
            Команда %s
            """, PROJECT_NAME, DOMAIN, token, PROJECT_NAME);
    }

    public static String registrationCodeSubject() {
        return "Код подтверждения";
    }

    public static String registrationCodeBody(String code) {
        return String.format("""
            Добрый день!

            Для подтверждения email в системе «%s» - перейдите по ссылке:
            https://%s/notify-settings
            
            В поле "✅ Подтверждение Email", введите данный код:
            %s

            С уважением,  
            Команда %s
            """, PROJECT_NAME,DOMAIN,code,PROJECT_NAME);
    }

    public static String passwordResetSubject() {
        return "Сброс пароля";
    }

    public static String passwordResetBody(String code) {
        return String.format("""
            Добрый день!

            Мы получили запрос на сброс пароля для вашей учётной записи в системе «%s».

            Чтобы установить новый пароль, перейдите по ссылке:
            https://%s/pass-reset?code=%s

            Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо — никаких действий предпринимать не нужно.

            С уважением,  
            Команда %s
            """, PROJECT_NAME, DOMAIN, code, PROJECT_NAME);
    }

    public static String ticketReplySubject(Long ticketId) {
        return "[Заявка#"+ticketId+"] Новое сообщение";
    }

    public static String ticketReplyBody(Long ticketId, Long portalId) {
        return String.format("""
            Добрый день!

            В заявке #%s появилось новое сообщение.

            Ознакомиться с ним можно по ссылке:
            https://%s/ticket-show/portal/%s/ticket/%s
            
            С уважением,  
            Команда %s
            """, ticketId, DOMAIN, ticketId, portalId, PROJECT_NAME);
    }

    public static String ticketCreatedSubject(Long ticketId) {
        return "Создана новая заявка #"+ticketId;
    }

    public static String ticketCreatedBody(Long ticketId, Long portalId) {
        return String.format("""
            Добрый день!

            Cоздана заявка #%s

            Ознакомиться с ней можно по ссылке:
            https://%s/ticket-show/portal/%s/ticket/%s
            
            С уважением,  
            Команда %s
            """, ticketId, DOMAIN, ticketId, portalId, PROJECT_NAME);
    }


    public static String portalCreatedSubject(Long portalId) {
        return "Создан новый портал #"+portalId;
    }

    public static String portalCreatedBody(Long portalId) {
        return String.format("""
            Добрый день!

            Cоздан портал #%s

            Ознакомиться с ним можно на странице "Порталы", в разделе "мои порталы".
            https://%s/portal-manager
            
            С уважением,  
            Команда %s
            """, portalId, DOMAIN, PROJECT_NAME);
    }


    public static String portalAddUserSubject(Long portalId) {
        return "Вас добавили к порталу #"+portalId;
    }

    public static String portalAddUserBody(Long portalId) {
        return String.format("""
            Добрый день!

            Ознакомиться с порталом можно на странице "Порталы", в разделе "Общие порталы".
            https://%s/portal-manager
            
            С уважением,  
            Команда %s
            """, portalId, DOMAIN, PROJECT_NAME);
    }

    public static String updateStatusTicketSubject(Long ticketId, TicketStatus ticketStatus) {
        return "Задан статус:"+ticketStatus+" для заявки#"+ticketId;
    }

    public static String updateStatusTicketBody(Long ticketId, Long portalId) {
        return String.format("""
            Добрый день!

            Ознакомиться с заявкой можно по ссылке:
            https://%s/ticket-show/portal/%s/ticket/%s
            
            С уважением,  
            Команда %s
            """, DOMAIN, portalId,ticketId, PROJECT_NAME);
    }

    public static String updatePriorityTicketSubject(Long ticketId, TicketPriority ticketPriority) {
        return "Задан приоритет:"+ticketPriority+" для заявки#"+ticketId;
    }

    public static String updatePriorityTicketBody(Long ticketId, Long portalId) {
        return String.format("""
            Добрый день!

            Ознакомиться с заявкой можно по ссылке:
            https://%s/ticket-show/portal/%s/ticket/%s
            
            С уважением,  
            Команда %s
            """, DOMAIN, portalId,ticketId, PROJECT_NAME);
    }

    public static String deletedTicketSubject(Long ticketId) {
        return "Удалена заявка#"+ticketId;
    }

    public static String deletedTicketBody(Long ticketId, String email) {
        return String.format("""
            Добрый день!

            Заявка #%s была удалена пользователем %s.
            
            С уважением,  
            Команда %s
            """, ticketId,email, PROJECT_NAME);
    }

    public static String deletedPortalSubject(Long portalId) {
        return "Удален портал#"+portalId;
    }

    public static String deletedPortalBody(Long portalId, String email) {
        return String.format("""
            Добрый день!

            Портал #%s был удален пользователем %s.
            
            С уважением,  
            Команда %s
            """, portalId,email, PROJECT_NAME);
    }
}
