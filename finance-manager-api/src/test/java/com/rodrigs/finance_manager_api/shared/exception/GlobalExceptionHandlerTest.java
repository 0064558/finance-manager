package com.rodrigs.finance_manager_api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemDetailFactory());

    @Test
    void shouldHideUnexpectedExceptionDetailsAndReturnTraceId() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");

        ProblemDetail problem = handler.handleUnexpectedException(
                new IllegalStateException("password=super-secret sql=select * from users"), request);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("super-secret", "select", "users");
        assertThat(problem.getProperties())
                .containsEntry("code", "INTERNAL_SERVER_ERROR")
                .containsKey("traceId")
                .containsKey("timestamp");
    }
}
