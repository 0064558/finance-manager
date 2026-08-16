package com.rodrigs.finance_manager_api.shared.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceIdFilter extends OncePerRequestFilter {

    private final ProblemDetailFactory problemDetailFactory;

    public RequestTraceIdFilter(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = problemDetailFactory.traceId(request);
        response.setHeader(ProblemDetailFactory.TRACE_ID_HEADER, traceId);
        filterChain.doFilter(request, response);
    }
}
