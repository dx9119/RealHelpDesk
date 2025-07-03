package com.ukhanov.RealHelpDesk.core.config;

import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WhiteUrlConfig {
    public static final List<String> WHITE_LIST_URLS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/register"
    );
}
