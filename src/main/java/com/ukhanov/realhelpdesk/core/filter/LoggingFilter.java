package com.ukhanov.realhelpdesk.core.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String REQUEST_ID = "requestId";
    public static final String CLIENT_IP = "clientIp";
    public static final String URI_REQUEST = "uriRequest";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = String.valueOf(RANDOM.nextLong());
        }

        MDC.put(REQUEST_ID, requestId);
        MDC.put(CLIENT_IP, getClientIpAddress(request));
        MDC.put(URI_REQUEST, getFullUrl(request));

        logger.info("Входящий запрос: [{}]",
                request.getMethod());

        try {
            filterChain.doFilter(request, response);

            logger.info("Исходящий запрос: [{}] HTTP статус: [{}]",
                    request.getMethod(),
                    response.getStatus());

        } finally {
            MDC.clear();
        }
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

    private String getFullUrl(HttpServletRequest request) {
        // query-string не логируем: в GET-параметрах бывают коды восстановления пароля
        return request.getRequestURL().toString();
    }

}
