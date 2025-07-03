package com.ukhanov.RealHelpDesk.core.filter;

import com.ukhanov.RealHelpDesk.core.config.WhiteUrlConfig;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.service.DecodeTokenService;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.service.ValidTokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final ValidTokenService validTokenService;
    private final DecodeTokenService decodeTokenService;

    public JwtAuthFilter(ValidTokenService tokenProcessingService, DecodeTokenService decodeTokenService) {
        this.validTokenService = tokenProcessingService;
        this.decodeTokenService = decodeTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // Пропускаем фильтр для URL из БС
        if (WhiteUrlConfig.WHITE_LIST_URLS
                .stream()
                .anyMatch(path::startsWith)) {
            logger.debug("Skip jwt filter: {}", path);

            filterChain.doFilter(request, response);
            return;
        }

        // Парсим access-токен
        TokenBearerResponse token = resolveToken(request);
        if (token == null) {
            logger.warn("The access-token is missing: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Auth-Token-Missing", "true");
            return;
        }

        try {
            // Проверяем токен
            validTokenService.lowLevelVerifyToken(token);

            // claims от access-токена для аутентификации
            String[] userInfo = decodeTokenService.decodeJwtForAuth(token);
            String userId = userInfo[0];
            String role = userInfo[1];

            // Аутентификация пользователя
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            logger.info("Authentication установлен в контекст: {}", auth.getName());

            // Следующий фильтр
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Access-Token-Expired", "true");
            logger.info("The token has expired {}: {}", path, e.getMessage());

        } catch (MalformedJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Malformed-Token", "true");
            logger.info("Incorrect token format{}: {}", path, e.getMessage());

        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Invalid-Token", "true");
            logger.info("JWT error {}: {}", path, e.getMessage());

        } catch (TokenException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Verify-Token-Failed", "true");
            logger.info("Access token rejected: {}", e.getMessage());
        }
    }

    // Извлекаем токен из заголовка Authorization.
    private TokenBearerResponse resolveToken(HttpServletRequest request) {
        TokenBearerResponse token = new TokenBearerResponse();
        String bearer = Objects.requireNonNullElse(request.getHeader("Authorization"), "");

        if (bearer.startsWith("Bearer ")) {
            logger.debug("The token was successfully received");
            token.setToken(bearer.substring(7));
            return token;
        }
        logger.debug("The Bearer token is missing in the header.");
        return null;
    }
}