package com.aiinpocket.n3n.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that sets up logging context for each HTTP request.
 * Provides trace ID and request ID for distributed tracing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String X_TRACE_ID = "X-Trace-Id";
    private static final String X_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            // Set trace ID (from header or generate new)
            String traceId = request.getHeader(X_TRACE_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = LogContext.generateTraceId();
            } else {
                LogContext.setTraceId(traceId);
            }

            // Set request ID
            String requestId = request.getHeader(X_REQUEST_ID);
            if (requestId == null || requestId.isBlank()) {
                requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
            }
            LogContext.setRequestId(requestId);

            // Add trace ID to response header for client correlation
            response.setHeader(X_TRACE_ID, traceId);
            response.setHeader(X_REQUEST_ID, requestId);

            // Log request start (only for API calls, skip static resources)
            String uri = request.getRequestURI();
            if (shouldLog(uri)) {
                log.info("REQUEST_START method={} uri={} query={}",
                        request.getMethod(),
                        uri,
                        request.getQueryString());
            }

            filterChain.doFilter(request, response);

        } finally {
            // Log request completion
            long duration = System.currentTimeMillis() - startTime;
            String uri = request.getRequestURI();
            if (shouldLog(uri)) {
                log.info("REQUEST_END method={} uri={} status={} duration_ms={}",
                        request.getMethod(),
                        uri,
                        response.getStatus(),
                        duration);
            }

            // Clear MDC
            LogContext.clear();
        }
    }

    /**
     * Determine if request should be logged.
     * Skip health checks and static resources.
     */
    private boolean shouldLog(String uri) {
        if (uri == null) return false;

        // Skip static resources
        if (uri.startsWith("/static/") ||
            uri.startsWith("/assets/") ||
            uri.endsWith(".js") ||
            uri.endsWith(".css") ||
            uri.endsWith(".html") ||
            uri.endsWith(".ico") ||
            uri.endsWith(".png") ||
            uri.endsWith(".jpg") ||
            uri.endsWith(".svg") ||
            uri.endsWith(".woff") ||
            uri.endsWith(".woff2")) {
            return false;
        }

        // Skip health checks (too frequent)
        if (uri.equals("/actuator/health") ||
            uri.equals("/actuator/health/liveness") ||
            uri.equals("/actuator/health/readiness")) {
            return false;
        }

        return true;
    }
}
