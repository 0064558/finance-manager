package com.rodrigs.finance_manager_api.user.repository;

import com.rodrigs.finance_manager_api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("""
            select u
            from User u
            where lower(u.email) = lower(:email)
            """)
    // Encontra um usuário pelo email, ignorando o case. Case é importante para emails,
        // mas aqui estamos ignorando o case para garantir que a busca seja insensível a maiúsculas e minúsculas.
    Optional<User> findByEmailIgnoreCase(String email);

    // Verifica se um usuário com o email fornecido já existe, ignorando o case. Isso é útil para validação de unicidade de email durante o registro de novos usuários.
    boolean existsByEmailIgnoreCase(String email);
}
