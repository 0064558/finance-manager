package com.rodrigs.finance_manager_api.transaction.rag;

import java.util.OptionalInt;

public final class TransactionEmbeddingException extends RuntimeException {
    private final OptionalInt statusCode;
    private final boolean configurationFailure;

    TransactionEmbeddingException(String message) {
        this(message, OptionalInt.empty(), false);
    }

    TransactionEmbeddingException(int statusCode) {
        this("Gemini returned HTTP " + statusCode + " for an embedding request", OptionalInt.of(statusCode),
                statusCode == 401 || statusCode == 403 || statusCode == 404);
    }

    private TransactionEmbeddingException(String message, OptionalInt statusCode, boolean configurationFailure) {
        super(message);
        this.statusCode = statusCode;
        this.configurationFailure = configurationFailure;
    }

    static TransactionEmbeddingException missingCredentials() {
        return new TransactionEmbeddingException("Gemini embedding credentials are unavailable", OptionalInt.empty(), true);
    }

    public OptionalInt statusCode() {
        return statusCode;
    }

    boolean configurationFailure() {
        return configurationFailure;
    }
}
