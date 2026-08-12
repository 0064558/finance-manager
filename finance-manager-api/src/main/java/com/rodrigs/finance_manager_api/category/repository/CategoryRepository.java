package com.rodrigs.finance_manager_api.category.repository;

import com.rodrigs.finance_manager_api.category.entity.Category;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // busca uma categoria especifica, mas so se ela pertencer ao user autenticado
    Optional<Category> findByIdAndUserId(UUID categoryId, UUID userId);

    // busca todas as categorias de um usuario, ordenadas pelo tipo de transacao e depois pelo nome (crescente)
    List<Category> findAllByUserIdOrderByTransactionTypeAscNameAsc(UUID userId);

    // verifica se ja existe uma categoria com o mesmo nome e tipo de transacao para o user autenticado
    boolean existsByUserIdAndTransactionTypeAndNameIgnoreCase(
            UUID userId,
            TransactionType transactionType,
            String name
    );
}
