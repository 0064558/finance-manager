package com.rodrigs.finance_manager_api.transfer.repository;

import com.rodrigs.finance_manager_api.transfer.entity.AccountTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AccountTransferRepository extends Repository<AccountTransfer, UUID> {

    Optional<AccountTransfer> findById(UUID id);

    Optional<AccountTransfer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<AccountTransfer> findAllByUserId(UUID userId, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO account_transfers (
                id, user_id, source_account_id, destination_account_id, amount, occurred_on, description, idempotency_key
            ) VALUES (:id, :userId, :sourceId, :destinationId, :amount, :occurredOn, :description, :key)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insert(@Param("id") UUID id, @Param("userId") UUID userId,
               @Param("sourceId") UUID sourceId, @Param("destinationId") UUID destinationId,
               @Param("amount") BigDecimal amount, @Param("occurredOn") LocalDate occurredOn,
               @Param("description") String description, @Param("key") String key);
}
