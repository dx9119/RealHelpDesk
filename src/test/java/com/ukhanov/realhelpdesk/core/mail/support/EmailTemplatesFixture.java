package com.ukhanov.realhelpdesk.core.mail.support;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.ukhanov.realhelpdesk.core.mail.config.EmailProperties;
import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;

public final class EmailTemplatesFixture {

    public static final String DOMAIN = "front.example.ru";
    public static final String PROJECT_NAME = "real help desk";

    private EmailTemplatesFixture() {
    }

    public static EmailProperties emailProperties() {
        EmailProperties emailProperties = new EmailProperties();
        emailProperties.setDomain(DOMAIN);
        emailProperties.setProjectName(PROJECT_NAME);
        return emailProperties;
    }

    public static MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    public static EmailTemplates emailTemplates() {
        return new EmailTemplates(messageSource(), emailProperties());
    }
}
