package com.rodrigs.finance_manager_api.financial_account.repository;

import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import com.rodrigs.finance_manager_api.report.repository.AccountBalanceProjection;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {
    // busca uma conta especifica, mas so se ela pertencer ao user autenticado
    Optional<FinancialAccount> findByIdAndUserId(UUID id, UUID userId);

    // busca todas as contas de um usuário, ordenadas pela data de criação (crescente)
    List<FinancialAccount> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    @Query("""
    SELECT
        account.id AS accountId,
        account.name AS accountName,

        account.initialBalance
            + COALESCE(SUM(
                CASE
                    WHEN tx.type = :incomeType
                    THEN tx.amount
                    ELSE 0
                END
            ), 0)
            - COALESCE(SUM(
                CASE
                    WHEN tx.type = :expenseType
                    THEN tx.amount
                    ELSE 0
                END
            ), 0) AS balance

    FROM FinancialAccount account
    LEFT JOIN Transaction tx
        ON tx.financialAccount.id = account.id

    WHERE account.user.id = :userId

    GROUP BY
        account.id,
        account.name,
        account.initialBalance,
        account.createdAt

    ORDER BY account.createdAt ASC
    """)
    List<AccountBalanceProjection> findCurrentBalancesByUserId(
            @Param("userId") UUID userId,
            @Param("incomeType") TransactionType incomeType,
            @Param("expenseType") TransactionType expenseType
    );
}
