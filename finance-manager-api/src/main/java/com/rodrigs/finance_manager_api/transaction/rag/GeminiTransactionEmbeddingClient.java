package com.rodrigs.finance_manager_api.transaction.rag;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import java.time.Duration;
import java.util.List;

final class GeminiTransactionEmbeddingClient implements TransactionEmbeddingClient, AutoCloseable {
    private static final int DIMENSIONS = 768;
    private static final Duration DOCUMENT_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpRetryOptions SINGLE_ATTEMPT = HttpRetryOptions.builder().attempts(1).build();

    private final Client client;
    private final String model;

    GeminiTransactionEmbeddingClient(Client client, String model) {
        this.client = client;
        this.model = model;
    }

    static GeminiTransactionEmbeddingClient create(String apiKey, String model) {
        Client client = apiKey.isBlank() ? null : Client.builder().apiKey(apiKey.strip()).vertexAI(false)
                .httpOptions(HttpOptions.builder().retryOptions(SINGLE_ATTEMPT).build())
                .build();
        return new GeminiTransactionEmbeddingClient(client, model);
    }

    @Override
    public float[] embedDocument(String text) {
        return embed(text, "RETRIEVAL_DOCUMENT", DOCUMENT_TIMEOUT);
    }

    @Override
    public float[] embedQuery(String text, Duration timeout) {
        return embed(text, "RETRIEVAL_QUERY", timeout);
    }

    private float[] embed(String text, String task, Duration timeout) {
        if (client == null) {
            throw TransactionEmbeddingException.missingCredentials();
        }
        if (timeout.compareTo(Duration.ofMillis(1)) < 0) {
            throw new TransactionEmbeddingException("Embedding request deadline expired");
        }
        int timeoutMillis = timeout.compareTo(DOCUMENT_TIMEOUT) > 0
                ? (int) DOCUMENT_TIMEOUT.toMillis() : (int) timeout.toMillis();
        EmbedContentConfig config = EmbedContentConfig.builder()
                .taskType(task)
                .outputDimensionality(DIMENSIONS)
                .httpOptions(HttpOptions.builder().timeout(timeoutMillis).retryOptions(SINGLE_ATTEMPT).build())
                .build();
        EmbedContentResponse response;
        try {
            response = client.models.embedContent(model, text, config);
        } catch (ApiException exception) {
            throw new TransactionEmbeddingException(exception.code());
        } catch (RuntimeException exception) {
            throw new TransactionEmbeddingException("Gemini embedding request failed");
        }

        var embeddings = response.embeddings().orElseGet(List::of);
        if (embeddings.size() != 1) {
            throw new TransactionEmbeddingException("Gemini returned an unexpected number of embeddings");
        }
        return validatedVector(embeddings.getFirst().values().orElseGet(List::of));
    }

    private float[] validatedVector(List<Float> values) {
        if (values.size() != DIMENSIONS) {
            throw new TransactionEmbeddingException("Gemini returned an embedding of unexpected dimensions");
        }
        float[] vector = new float[DIMENSIONS];
        boolean nonZero = false;
        for (int index = 0; index < DIMENSIONS; index++) {
            Float value = values.get(index);
            if (value == null || !Float.isFinite(value)) {
                throw new TransactionEmbeddingException("Gemini returned an invalid embedding value");
            }
            vector[index] = value;
            nonZero |= value != 0f;
        }
        if (!nonZero) {
            throw new TransactionEmbeddingException("Gemini returned a zero embedding");
        }
        return vector;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
