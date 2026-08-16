package com.rodrigs.finance_manager_api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ProblemDetailFactory {

    public static final String TRACE_ID_ATTRIBUTE = ProblemDetailFactory.class.getName() + ".traceId";
    public static final String TRACE_ID_HEADER = "X-Request-Id";

    public ProblemDetail create(
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("timestamp", OffsetDateTime.now());
        problemDetail.setProperty("traceId", traceId(request));
        return problemDetail;
    }

    public String traceId(HttpServletRequest request) {
        Object existingTraceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (existingTraceId instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }

        String traceId = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        return traceId;
    }
}
