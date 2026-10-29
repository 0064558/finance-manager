package com.rodrigs.finance_manager_api.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared infrastructure for integration tests against the same PostgreSQL container.
 * Each test method starts with an empty application schema.
 */
@ContextConfiguration(initializers = PostgresIntegrationTest.TestDatabaseGuard.class)
public abstract class PostgresIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("finance_manager_test")
            .withUsername("finance_manager_test")
            .withPassword("finance_manager_test");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    /** Refuse to initialize Spring (and therefore Flyway) with any other database. */
    public static class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var environment = context.getEnvironment();
            if (!java.util.Set.of(environment.getActiveProfiles()).equals(java.util.Set.of("test"))
                    || !POSTGRES.getJdbcUrl().equals(environment.getProperty("spring.datasource.url"))
                    || !POSTGRES.getJdbcUrl().equals(environment.getProperty("spring.flyway.url"))) {
                throw new IllegalStateException("Tests require only the test profile and the disposable PostgreSQL container.");
            }
        }
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE transactions, categories, financial_accounts, users RESTART IDENTITY CASCADE");
    }
}
