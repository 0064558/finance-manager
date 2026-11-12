package com.rodrigs.finance_manager_api.transaction.rag;

import java.time.Duration;

public interface TransactionEmbeddingClient {
    float[] embedDocument(String text);

    float[] embedQuery(String text, Duration timeout);
}
