package com.ukhanov.realhelpdesk.core.filter;

import com.ukhanov.realhelpdesk.core.config.WhiteUrlConfig;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.AccessTokenAuthService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.DecodeTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.ValidTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.JwtClaims;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final ValidTokenService validTokenService;
    private final DecodeTokenService decodeTokenService;
    private final AccessTokenAuthService accessTokenAuthService;
    private final AntPathMatcher antPathMatcher;

    public JwtAuthFilter(ValidTokenService tokenProcessingService, DecodeTokenService decodeTokenService,
        AccessTokenAuthService accessTokenAuthService, AntPathMatcher antPathMatcher) {
        this.validTokenService = tokenProcessingService;
        this.decodeTokenService = decodeTokenService;
        this.accessTokenAuthService = accessTokenAuthService;
      this.antPathMatcher = antPathMatcher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        //пропускаем OPTIONS для CORS запросов
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Пропускаем фильтр для URL из БС
        if (WhiteUrlConfig.WHITE_LIST_URLS
            .stream()
            .anyMatch(whiteListedPath -> antPathMatcher.match(whiteListedPath, path))) {

            filterChain.doFilter(request, response);
            return;
        }

        // Парсим access-токен
        TokenBearerResponse token = resolveToken(request);
        if (token == null) {
            logger.warn("Нет access-token: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Auth-Token-Missing", "true");
            return;
        }

        try {
            // Проверяем подпись, срок, issuer/audience и тип токена
            validTokenService.lowLevelVerifyToken(token, JwtClaims.TYPE_ACCESS);

            Claims claims = decodeTokenService.decodeJwtClaims(token);

            // Проверяем пользователя в БД: статус и версию токена (отзыв)
            UserModel user = accessTokenAuthService.loadVerifiedUser(claims);

            // Аутентификация пользователя (роль берём из БД, а не из токена)
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user.getId().toString(),
                    null,
                    List.of(new SimpleGrantedAuthority(user.getUserRole().name()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Следующий фильтр
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Access-Token-Expired", "true");
            logger.error("Токен истек {}: {}", path, e.getMessage());

        } catch (MalformedJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Malformed-Token", "true");
            logger.error("Не корректный формат токена доступа, {}: {}", path, e.getMessage());

        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Invalid-Token", "true");
            logger.error("JWT ошибка {}: {}", path, e.getMessage());

        } catch (TokenException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("X-Verify-Token-Failed", "true");
            logger.error("Токен доступа отвергнут: {}", e.getMessage());
        }
    }

    // Извлекаем токен из куки
    private TokenBearerResponse resolveToken(HttpServletRequest request) {
        TokenBearerResponse token = new TokenBearerResponse();
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    token.setToken(cookie.getValue());
                    return token;
                }
            }
        }
        logger.debug("Токен доступа отсутствует в файлах cookie.");
        return null;
    }
}