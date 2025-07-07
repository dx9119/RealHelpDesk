package com.ukhanov.realhelpdesk.core.config;

import com.ukhanov.realhelpdesk.core.filter.JwtAuthFilter;
import com.ukhanov.realhelpdesk.core.filter.LoggingFilter;
import com.ukhanov.realhelpdesk.core.security.user.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

    private final CustomUserDetailsService customUserDetailsService; // UserDetailsService

    public WebSecurityConfiguration(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           LoggingFilter loggingFilter,
                                           JwtAuthFilter jwtAuthFilter) throws Exception {
        // Фильтры
        // Важно: порядок фильтров нужен для корректного логирования аутентификации через JWT
        http.addFilterBefore(jwtAuthFilter, BasicAuthenticationFilter.class);
        http.addFilterBefore(loggingFilter, JwtAuthFilter.class);

        // Доступ
        http.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
                .requestMatchers(WhiteUrlConfig.WHITE_LIST_URLS.toArray(String[]::new)).permitAll() // Доступ к whiteLi, как лучше сделать?
                .anyRequest().authenticated());

        http.headers(Customizer.withDefaults());
        http.anonymous(AbstractHttpConfigurer::disable); // Анонимный пользователь не нужен
        http.csrf(AbstractHttpConfigurer::disable); // cookie-based сессий нет, отключаем CSRF и менеджер сессий
        http.sessionManagement(sessionManagement -> sessionManagement
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    // Шифрование пароля
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Выбор провайдера аутентификации
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity.getSharedObject(AuthenticationManagerBuilder.class)
                .authenticationProvider(authenticationProvider())
                .build();
    }

    // Логика аутентификации(загрузка пользователя, проверка пароля)
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(customUserDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        return daoAuthenticationProvider;
    }

}