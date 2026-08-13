package com.otboo.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdMdcFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_REQUEST_ID_KEY = "requestId";

    private static final String REQUEST_ID_ATTRIBUTE =
            RequestIdMdcFilter.class.getName() + ".REQUEST_ID";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = resolveRequestId(request);

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        Object requestIdAttribute = request.getAttribute(REQUEST_ID_ATTRIBUTE);

        if (requestIdAttribute instanceof String requestId && isValidRequestId(requestId)) {
            return requestId;
        }

        String requestIdHeader = request.getHeader(REQUEST_ID_HEADER);

        if (isValidRequestId(requestIdHeader)) {
            return requestIdHeader;
        }

        return UUID.randomUUID().toString();
    }

    private boolean isValidRequestId(String requestId) {
        if (!StringUtils.hasText(requestId) || requestId.length() != 36) {
            return false;
        }

        try {
            UUID.fromString(requestId);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
