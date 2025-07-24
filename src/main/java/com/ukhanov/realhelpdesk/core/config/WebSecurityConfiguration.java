package com.ukhanov.realhelpdesk.core.config;

import com.ukhanov.realhelpdesk.core.filter.JwtAuthFilter;
import com.ukhanov.realhelpdesk.core.filter.LoggingFilter;
import com.ukhanov.realhelpdesk.core.security.user.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Correct import for HttpMethod
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List; // For List.of()

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final JwtAuthFilter jwtAuthFilter;
    private final LoggingFilter loggingFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public WebSecurityConfiguration(JwtAuthFilter jwtAuthFilter,
        LoggingFilter loggingFilter, CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.loggingFilter = loggingFilter;
      this.corsConfigurationSource = corsConfigurationSource;
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception { // Remove filters from params, inject them
        // Фильтры
        // Важно: порядок фильтров нужен для корректного логирования аутентификации через JWT
        http.addFilterBefore(jwtAuthFilter, BasicAuthenticationFilter.class);
        http.addFilterBefore(loggingFilter, JwtAuthFilter.class);
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        // Доступ
        http.authorizeHttpRequests(authorize -> authorize
            // 1. Разрешаем все OPTIONS запросы БЕЗ АУТЕНТИКАЦИИ.
            // Это позволяет Preflight запросам проходить ДО ВСЕХ других фильтров аутентификации.
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // 2. Разрешаем доступ к публичным URL
            // Эти пути не будут требовать JWT токена
            .requestMatchers(WhiteUrlConfig.WHITE_LIST_URLS.toArray(String[]::new)).permitAll()

            // 3. Все остальные запросы требуют аутентификации.
            .anyRequest().authenticated());

        http.headers(Customizer.withDefaults());
        http.anonymous(AbstractHttpConfigurer::disable);
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(sessionManagement -> sessionManagement
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // CORS с помощью настроек из corsConfigurationSource
        http.cors(Customizer.withDefaults());

        return http.build();
    }

}