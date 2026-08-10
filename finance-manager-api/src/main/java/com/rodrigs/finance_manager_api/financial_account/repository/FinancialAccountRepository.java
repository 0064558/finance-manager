package com.rodrigs.finance_manager_api.financial_account.repository;

import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
