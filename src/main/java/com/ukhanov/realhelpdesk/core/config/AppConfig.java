package com.ukhanov.realhelpdesk.core.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ukhanov.realhelpdesk.core.security.user.service.CustomUserDetailsService;

import lombok.RequiredArgsConstructor;

// Периодические задачи (повторы оповещений) считаются по расписанию, а не по запросам
@RequiredArgsConstructor
@EnableScheduling
@Configuration
public class AppConfig {

    /**
     * Заголовки ответа, доступные JavaScript кросс-доменного клиента (CORS exposedHeaders). Список закрытый: всё, что не перечислено,
     * браузер скрывает от fetch.
     */
    static final List<String> EXPOSED_HEADERS = List.of(
            // 201 Created и 206 Partial Content
            "Location",
            // Файлы: имя при скачивании и докачка по Range
            "Content-Disposition", "Content-Range", "Accept-Ranges", "ETag",
            // 429
            "Retry-After",
            // Маркеры причины 401 из JwtAuthFilter: SPA отличает «токен истёк» от «токена нет»
            "X-Access-Token-Expired", "X-Auth-Token-Missing", "X-Malformed-Token", "X-Invalid-Token", "X-Verify-Token-Failed");

    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public AntPathMatcher antPathMatcher() {
        return new AntPathMatcher();
    }

    // Шифрование пароля
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Выбор провайдера аутентификации
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity.getSharedObject(AuthenticationManagerBuilder.class).authenticationProvider(authenticationProvider()).build();
    }

    // Логика аутентификации(загрузка пользователя, проверка пароля)
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(customUserDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        return daoAuthenticationProvider;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        // Список origin'ов из конфигурации (см. CorsProperties), в коде домены не держим.
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Без allowedHeaders любой preflight с Access-Control-Request-Headers (а его шлёт
        // браузер для Content-Type: application/json) отклоняется 403 Invalid CORS request —
        // то есть все JSON-мутации с другого origin. "*" вместе с allowCredentials безопасен:
        // Spring подставляет запрошенные заголовки, а не отдаёт литерал "*".
        configuration.setAllowedHeaders(List.of("*"));
        // Заголовки, которые клиент может прочитать из ответа: по умолчанию JS доступны
        // только safelisted (Cache-Control, Content-Type и т.п.), всё остальное скрыто.
        // Маркеры 401 нужны SPA для честного refresh, Location — после 201, файловые
        // заголовки — для докачки, Retry-After — для подсказки «повторите через N сек».
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
