package com.rodrigs.finance_manager_api.transaction.repository;

import com.rodrigs.finance_manager_api.report.repository.ReportTotalsProjection;
import com.rodrigs.finance_manager_api.report.repository.CashFlowProjection;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // Verifica se existe uma transação associada a uma conta financeira específica e a um usuário específico
    boolean existsByFinancialAccount_IdAndUser_Id(
            UUID accountId,
            UUID userId
    );

    // Verifica se existe uma transação associada a uma categoria específica e a um usuário específico
    boolean existsByCategory_IdAndUser_Id(
            UUID categoryId,
            UUID userId
    );

    // Recupera uma transação específica associada a um usuário específico
    Optional<Transaction> findByIdAndUser_Id(
            UUID transactionId,
            UUID userId
    );

    // Recupera todas as transações associadas a um usuário específico, com paginação
    Page<Transaction> findAllByUser_Id(
            UUID userId,
            Pageable pageable
    );

    // Calcula os totais de receita e despesa para um usuário específico em um período específico
    @Query("""
    SELECT
        COALESCE(SUM(
            CASE WHEN t.type = :incomeType
            THEN t.amount
            ELSE 0
            END
        ), 0) AS totalIncome,

        COALESCE(SUM(
            CASE WHEN t.type = :expenseType
            THEN t.amount
            ELSE 0
            END
        ), 0) AS totalExpense

    FROM Transaction t
    WHERE t.user.id = :userId
      AND t.occurredOn BETWEEN :startDate AND :endDate
    """)
    ReportTotalsProjection findTotalsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("incomeType") TransactionType incomeType,
            @Param("expenseType") TransactionType expenseType
    );

    // Agrupa receitas e despesas por dia para alimentar séries temporais de relatórios.
    @Query("""
    SELECT
        t.occurredOn AS occurredOn,

        COALESCE(SUM(
            CASE WHEN t.type = :incomeType
            THEN t.amount
            ELSE 0
            END
        ), 0) AS totalIncome,

        COALESCE(SUM(
            CASE WHEN t.type = :expenseType
            THEN t.amount
            ELSE 0
            END
        ), 0) AS totalExpense

    FROM Transaction t
    WHERE t.user.id = :userId
      AND t.occurredOn BETWEEN :startDate AND :endDate
    GROUP BY t.occurredOn
    ORDER BY t.occurredOn
    """)
    List<CashFlowProjection> findCashFlowByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("incomeType") TransactionType incomeType,
            @Param("expenseType") TransactionType expenseType
    );
}
