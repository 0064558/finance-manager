package com.rodrigs.finance_manager_api.transaction.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRagPropertiesTest {
    private static final String PREFIX = "finance-manager.transaction-chat.rag";
    private static final UUID FIRST = UUID.fromString("9f5192b3-cda8-46a2-afac-341fa0fca8f7");
    private static final UUID SECOND = UUID.fromString("87c1939e-d713-4e0c-9308-01f7ec1977e8");

    @Test
    void usesThePilotDefaultsAndRequiresAnExplicitAllowlist() {
        var defaults = bind(Map.of());
        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.searchTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(defaults.minSimilarity()).isEqualTo(0.55);
        assertThat(defaults.maxExamples()).isEqualTo(3);
        assertThat(defaults.backfillLimit()).isEqualTo(100);
        assertThat(defaults.version()).isEqualTo("gemini-embedding-001:768:normalized-v1");

        var emptyPilot = bind(Map.of(PREFIX + ".enabled", "true", PREFIX + ".pilot-user-ids", ""));
        assertThat(emptyPilot.permits(FIRST)).isFalse();
    }

    @Test
    void permitsOnlyConfiguredUsersWhileTheFlagIsOn() {
        var pilot = bind(Map.of(PREFIX + ".enabled", "true", PREFIX + ".pilot-user-ids", FIRST + "," + SECOND));
        assertThat(pilot.permits(FIRST)).isTrue();
        assertThat(pilot.permits(SECOND)).isTrue();
        assertThat(pilot.permits(UUID.randomUUID())).isFalse();

        var disabled = bind(Map.of(PREFIX + ".pilot-user-ids", FIRST.toString()));
        assertThat(disabled.permits(FIRST)).isFalse();
    }

    private TransactionRagProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate(PREFIX, TransactionRagProperties.class);
    }
}
