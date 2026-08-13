package com.rodrigs.finance_manager_api.transaction.repository;

import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // verifica se uma transação existe para uma conta específica e um usuário específico
    boolean existsByAccountIdAndUserId(UUID accountId, UUID userId);

    // verifica se uma transação existe para uma categoria específica e um usuário específico
    boolean existsByCategoryIdAndUserId(UUID categoryId, UUID userId);
}
