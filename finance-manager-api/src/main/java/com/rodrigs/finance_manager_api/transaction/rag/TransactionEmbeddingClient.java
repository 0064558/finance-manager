package com.rodrigs.finance_manager_api.transaction.rag;

import java.time.Duration;

public interface TransactionEmbeddingClient {
    float[] embedDocument(String text);

    float[] embedQuery(String text, Duration timeout);

    static boolean hasFeatures(float[] vector) {
        for (float value : vector) {
            if (value != 0f) {
                return true;
            }
        }
        return false;
    }
}
