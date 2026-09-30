package com.ukhanov.realhelpdesk.core.mail.support;

import org.springframework.context.support.ResourceBundleMessageSource;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;

public final class EmailTemplatesFixture {

    private EmailTemplatesFixture() {
    }

    public static EmailTemplates emailTemplates() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        return new EmailTemplates(messageSource);
    }
}
