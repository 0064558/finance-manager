package com.rodrigs.finance_manager_api.support;

import com.rodrigs.finance_manager_api.FinanceManagerApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(classes = FinanceManagerApiApplication.class)
class DatabaseSchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyAllFlywayMigrationsToPostgres() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true",
                Integer.class
        );

        Integer applicationTables = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'financial_accounts', 'categories', 'transactions')
                """, Integer.class);

        assertThat(appliedMigrations).isEqualTo(4);
        assertThat(applicationTables).isEqualTo(4);
    }

    @Test
    void shouldKeepOwnershipAndFinancialConstraintsInDatabase() {
        Integer constraints = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_constraint
                WHERE conname IN (
                    'uq_accounts_id_user',
                    'uq_categories_id_user',
                    'fk_transactions_account_same_user',
                    'fk_transactions_category_same_user',
                    'ck_transactions_amount_positive'
                )
                """, Integer.class);

        assertThat(constraints).isEqualTo(5);
    }
}
