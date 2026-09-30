package com.ukhanov.realhelpdesk.core.filter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    public static final String REQUEST_ID = "requestId";
    public static final String CLIENT_IP = "clientIp";
    public static final String URI_REQUEST = "uriRequest";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        MDC.put(REQUEST_ID, resolveRequestId(request));
        MDC.put(CLIENT_IP, getClientIpAddress(request));
        // query-string не кладём в MDC: в GET-параметрах бывают коды восстановления пароля
        MDC.put(URI_REQUEST, request.getRequestURI());

        long startedNs = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failed = true;
            logger.error("HTTP {} {} завершился исключением за {} мс", request.getMethod(), request.getRequestURI(), elapsedMs(startedNs),
                    ex);
            throw ex;
        } finally {
            if (!failed) {
                logExchange(request, response, startedNs);
            }
            MDC.clear();
        }
    }

    // Успех — DEBUG (только профиль debug). 4xx — INFO, 5xx — ERROR: это видно в prod.
    private void logExchange(HttpServletRequest request, HttpServletResponse response, long startedNs) {
        int status = response.getStatus();
        long tookMs = elapsedMs(startedNs);
        if (status >= 500) {
            logger.error("HTTP {} {} -> {} за {} мс", request.getMethod(), request.getRequestURI(), status, tookMs);
        } else if (status >= 400) {
            logger.info("HTTP {} {} -> {} за {} мс", request.getMethod(), request.getRequestURI(), status, tookMs);
        } else {
            logger.debug("HTTP {} {} -> {} за {} мс", request.getMethod(), request.getRequestURI(), status, tookMs);
        }
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    // Чужой X-Request-ID принимаем только как безопасный идентификатор, иначе подменяем своим.
    private static String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-ID");
        if (header != null && SAFE_REQUEST_ID.matcher(header).matches()) {
            return header;
        }
        return Long.toUnsignedString(RANDOM.nextLong());
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip : "ip null";
    }

}
