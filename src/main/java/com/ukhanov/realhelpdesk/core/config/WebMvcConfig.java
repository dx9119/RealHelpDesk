package com.ukhanov.realhelpdesk.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Разрешить CORS для всех путей
                .allowedOrigins("*") // Разрешить запросы со всех доменов
                .allowedMethods("*") // Разрешить все HTTP методы
                .allowedHeaders("*") // Разрешить все заголовки
                .allowCredentials(false) // Отключить передачу учетных данных (куки, заголовки авторизации)
                .maxAge(3600); // Продолжительность кэширования предварительного запроса (в секундах)
    }
}