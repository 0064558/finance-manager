package com.rodrigs.finance_manager_api.transaction.service;

import com.google.genai.errors.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class TransactionProviderFailureTest {
    @ParameterizedTest
    @CsvSource({"429,rate_limited", "401,authentication", "403,authentication", "400,request_rejected",
            "500,provider_error", "503,provider_error", "504,provider_error"})
    void classifiesWrappedHttpFailuresWithoutCopyingTheirMessages(int status, String reason) {
        var failure = new RuntimeException("private wrapper", new ApiException(status, "private status", "private body"));
        var result = TransactionProviderFailure.from(failure);
        assertThat(result).isEqualTo(new TransactionProviderFailure(reason, status, "ApiException"));
        assertThat(result.toString()).doesNotContain("private");
    }

    @Test
    void distinguishesSocketAndHttpTimeoutsFromOtherTransportErrors() {
        for (var failure : new Exception[]{new SocketTimeoutException("private"), new HttpTimeoutException("private")}) {
            assertThat(TransactionProviderFailure.from(new RuntimeException(failure)).reason()).isEqualTo("timeout");
        }
        assertThat(TransactionProviderFailure.from(new RuntimeException(new IOException("private"))).reason())
                .isEqualTo("transport");
    }

    @Test
    void distinguishesMalformedJsonAndHandlesCyclicCauses() {
        Throwable malformed = catchThrowable(() -> JsonMapper.builder().build().readTree("{private"));
        assertThat(TransactionProviderFailure.from(new RuntimeException(malformed)).reason()).isEqualTo("invalid_response");
        var outer = new RuntimeException("private outer");
        var inner = new IllegalStateException("private inner", outer);
        outer.initCause(inner);
        assertThat(TransactionProviderFailure.from(outer).reason()).isEqualTo("unknown");
    }

    @Test
    void preservesTheTimeoutClassificationWhenTheSdkWrapsACancelledIoOperation() {
        var timeout = new InterruptedIOException("private timeout");
        timeout.initCause(new IOException("private cancellation"));
        var failure = TransactionProviderFailure.from(new RuntimeException(timeout));
        assertThat(failure.reason()).isEqualTo("timeout");
        assertThat(failure.cause()).isEqualTo("InterruptedIOException");
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void retriesOnlyTransientFailuresAndNeverAnInterruptedThread() {
        assertThat(TransactionProviderFailure.from(new RuntimeException(new IOException("private"))).retryable()).isTrue();
        for (int status : new int[]{400, 401, 403, 429, 501}) {
            assertThat(TransactionProviderFailure.from(new ApiException(status, "private", "private")).retryable()).isFalse();
        }
        Throwable malformed = catchThrowable(() -> JsonMapper.builder().build().readTree("{private"));
        assertThat(TransactionProviderFailure.from(new RuntimeException(malformed)).retryable()).isFalse();
        assertThat(TransactionProviderFailure.from(new RuntimeException(new InterruptedException())).retryable()).isFalse();
        Thread.currentThread().interrupt();
        try {
            assertThat(TransactionProviderFailure.from(new ApiException(503, "private", "private")).retryable()).isFalse();
        } finally {
            Thread.interrupted();
        }
    }
}
