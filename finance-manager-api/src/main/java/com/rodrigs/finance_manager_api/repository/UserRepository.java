package com.rodrigs.finance_manager_api.repository;

import com.rodrigs.finance_manager_api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("""
            select u
            from User u
            where lower(u.email) = lower(:email)
            """)
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);;
}
