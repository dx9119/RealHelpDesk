package com.ukhanov.RealHelpDesk.core.filter;

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
import java.util.UUID;

@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

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
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(REQUEST_ID, requestId);
        MDC.put(CLIENT_IP, getClientIpAddress(request));
        MDC.put(URI_REQUEST, getFullUrl(request));

        logger.info("Incoming Request: [{}]",
                request.getMethod());

        try {
            filterChain.doFilter(request, response);

            logger.info("Outgoing Response: [{}] HTTP Status: [{}]",
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
        StringBuffer url = request.getRequestURL();
        if (request.getQueryString() != null) {
            url.append('?').append(request.getQueryString());
        }
        return url.toString();
    }

}
