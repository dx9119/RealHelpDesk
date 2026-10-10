package com.ukhanov.realhelpdesk.core.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "email")
@Getter
@Setter
public class EmailProperties {
    private String notify;
    private String from;
    private String domain;
    private String projectName;
}
