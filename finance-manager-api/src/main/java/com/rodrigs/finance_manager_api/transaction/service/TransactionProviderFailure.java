package com.rodrigs.finance_manager_api.transaction.service;

import com.google.genai.errors.ApiException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/** Only fixed classifications and exception types may reach operational logs. */
record TransactionProviderFailure(String reason, int status, String cause) {
    static TransactionProviderFailure from(Throwable failure) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Throwable current = failure;
        TransactionProviderFailure result = new TransactionProviderFailure("unknown", 0,
                failure.getClass().getSimpleName());
        while (current != null && visited.size() < 32 && visited.add(current)) {
            String type = current.getClass().getSimpleName();
            if (current instanceof ApiException api) {
                int status = api.code();
                String reason = status == 429 ? "rate_limited" : status == 401 || status == 403
                        ? "authentication" : status >= 500 ? "provider_error" : "request_rejected";
                return new TransactionProviderFailure(reason, status, type);
            }
            if (current instanceof InterruptedException || current instanceof CancellationException) {
                return new TransactionProviderFailure("interrupted", 0, type);
            }
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException
                    || current instanceof InterruptedIOException || current instanceof TimeoutException) {
                return new TransactionProviderFailure("timeout", 0, type);
            }
            if (current instanceof tools.jackson.core.JacksonException
                    || current instanceof com.fasterxml.jackson.core.JsonProcessingException) {
                result = new TransactionProviderFailure("invalid_response", 0, type);
            } else if (current instanceof IOException) {
                result = new TransactionProviderFailure("transport", 0, type);
            } else if (result.reason().equals("unknown")) {
                result = new TransactionProviderFailure("unknown", 0, type);
            }
            current = current.getCause();
        }
        return result;
    }

    boolean retryable() {
        return !Thread.currentThread().isInterrupted() && (status == 500 || status == 502 || status == 503
                || status == 504 || reason.equals("timeout") || reason.equals("transport"));
    }
}
