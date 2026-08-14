package com.rodrigs.finance_manager_api.transaction.repository;

import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

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
}
