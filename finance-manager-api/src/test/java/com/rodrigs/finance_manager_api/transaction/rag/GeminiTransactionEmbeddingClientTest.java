package com.rodrigs.finance_manager_api.transaction.rag;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiTransactionEmbeddingClientTest {
    private static final String MODEL = "gemini-embedding-001";

    @Test
    void buildsDocumentAndQueryRequestsWithDifferentTasksAndBoundedTimeouts() {
        var bodies = new ArrayList<String>();
        var timeouts = new ArrayList<Long>();
        String vector = "0.25," + String.join(",", Collections.nCopies(767, "0"));
        try (var client = client(chain -> {
            var buffer = new Buffer();
            chain.request().body().writeTo(buffer);
            bodies.add(buffer.readUtf8());
            timeouts.add(chain.call().timeout().timeoutNanos());
            assertThat(chain.request().url().encodedPath()).contains(MODEL);
            return response(chain.request(), 200, "{\"embeddings\":[{\"values\":[" + vector + "]}]}");
        })) {
            assertThat(client.embedDocument("almoço durante o plantão")).hasSize(768);
            assertThat(client.embedQuery("refeição no trabalho", Duration.ofMillis(400))).hasSize(768);
        }

        assertThat(bodies).satisfiesExactly(
                document -> assertThat(document).contains("\"taskType\":\"RETRIEVAL_DOCUMENT\"", "\"outputDimensionality\":768"),
                query -> assertThat(query).contains("\"taskType\":\"RETRIEVAL_QUERY\"", "\"outputDimensionality\":768"));
        assertThat(timeouts).containsExactly(TimeUnit.SECONDS.toNanos(10), TimeUnit.MILLISECONDS.toNanos(400));
    }

    @Test
    void stripsProviderDetailsFromFailures() {
        var attempts = new AtomicInteger();
        try (var client = client(chain -> {
            attempts.incrementAndGet();
            return response(chain.request(), 429,
                    "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"private input\"}}");
        })) {
            assertThatThrownBy(() -> client.embedQuery("test transaction", Duration.ofSeconds(1)))
                    .isInstanceOfSatisfying(TransactionEmbeddingException.class, error -> {
                        assertThat(error.statusCode()).hasValue(429);
                        assertThat(error).hasMessageNotContaining("private input").hasNoCause();
                    });
        }
        assertThat(attempts).hasValue(1);
    }

    @Test
    void rejectsExpiredDeadlinesAndInvalidVectors() {
        var attempts = new AtomicLong();
        try (var client = client(chain -> {
            attempts.incrementAndGet();
            return response(chain.request(), 200, "{\"embeddings\":[{\"values\":[0.25]}]}");
        })) {
            assertThatThrownBy(() -> client.embedQuery("test transaction", Duration.ofNanos(1)))
                    .isInstanceOf(TransactionEmbeddingException.class)
                    .hasMessageContaining("deadline");
            assertThat(attempts).hasValue(0);
            assertThatThrownBy(() -> client.embedDocument("test transaction"))
                    .isInstanceOf(TransactionEmbeddingException.class)
                    .hasMessageContaining("dimensions");
        }
    }

    private GeminiTransactionEmbeddingClient client(Interceptor interceptor) {
        var http = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        var sdk = Client.builder().apiKey("fixture-key").vertexAI(false)
                .clientOptions(ClientOptions.builder().customHttpClient(http).build()).build();
        return new GeminiTransactionEmbeddingClient(sdk, MODEL);
    }

    private Response response(Request request, int status, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }
}
