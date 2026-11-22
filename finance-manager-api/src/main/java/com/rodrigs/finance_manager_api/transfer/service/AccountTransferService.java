package com.rodrigs.finance_manager_api.transfer.service;

import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.IdempotencyConflictException;
import com.rodrigs.finance_manager_api.shared.exception.InvalidIdempotencyKeyException;
import com.rodrigs.finance_manager_api.shared.exception.SameTransferAccountException;
import com.rodrigs.finance_manager_api.transfer.dto.AccountTransferResponseDTO;
import com.rodrigs.finance_manager_api.transfer.dto.CreateAccountTransferRequestDTO;
import com.rodrigs.finance_manager_api.transfer.entity.AccountTransfer;
import com.rodrigs.finance_manager_api.transfer.repository.AccountTransferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class AccountTransferService {

    private final FinancialAccountRepository accounts;
    private final AccountTransferRepository transfers;

    public AccountTransferService(FinancialAccountRepository accounts, AccountTransferRepository transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @Transactional
    public AccountTransferResponseDTO create(UUID userId, CreateAccountTransferRequestDTO request, String key) {
        if (key != null && (key.isBlank() || key.length() > 200)) {
            throw new InvalidIdempotencyKeyException();
        }
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new SameTransferAccountException();
        }

        // Use the account mutation lock and a consistent order for transfers in either direction.
        Stream.of(request.sourceAccountId(), request.destinationAccountId()).sorted().forEachOrdered(accountId ->
                accounts.findOwnedForUpdate(accountId, userId).orElseThrow(FinancialAccountNotFoundException::new));

        UUID id = UUID.randomUUID();
        String description = request.description() == null ? null : request.description().trim();
        int inserted = transfers.insert(id, userId, request.sourceAccountId(), request.destinationAccountId(),
                request.amount(), request.occurredOn(), description, key);
        if (inserted == 1) {
            return toResponse(transfers.findById(id).orElseThrow());
        }

        AccountTransfer saved = transfers.findByUserIdAndIdempotencyKey(userId, key).orElseThrow();
        if (!saved.getSourceAccountId().equals(request.sourceAccountId())
                || !saved.getDestinationAccountId().equals(request.destinationAccountId())
                || saved.getAmount().compareTo(request.amount()) != 0
                || !saved.getOccurredOn().equals(request.occurredOn())
                || !Objects.equals(saved.getDescription(), description)) {
            throw new IdempotencyConflictException();
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AccountTransferResponseDTO> findAll(UUID userId, Pageable pageable) {
        return transfers.findAllByUserId(userId, pageable).map(this::toResponse);
    }

    private AccountTransferResponseDTO toResponse(AccountTransfer transfer) {
        return new AccountTransferResponseDTO(transfer.getId(), transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(), transfer.getAmount(), transfer.getOccurredOn(),
                transfer.getDescription(), transfer.getCreatedAt());
    }
}
