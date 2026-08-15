package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.category.entity.Category;
import com.rodrigs.finance_manager_api.category.repository.CategoryRepository;
import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.*;
import com.rodrigs.finance_manager_api.transaction.dto.CreateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.dto.UpdateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import com.rodrigs.finance_manager_api.transaction.repository.specification.TransactionSpecifications;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, FinancialAccountRepository financialAccountRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public TransactionResponseDTO createTransaction(UUID authenticatedUserId, CreateTransactionRequestDTO requestDTO) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(UserNotFoundException::new);
        FinancialAccount account = financialAccountRepository.findByIdAndUserId(requestDTO.accountId(), authenticatedUserId)
                .orElseThrow(FinancialAccountNotFoundException::new);
        Category category = categoryRepository.findByIdAndUserId(requestDTO.categoryId(), authenticatedUserId)
                .orElseThrow(CategoryNotFoundException::new);

        if (requestDTO.type() != category.getTransactionType()) {
            throw new TransactionTypeMismatchException();
        }

        String description = requestDTO.description() == null ? null : requestDTO.description().trim();

        Transaction transaction = new Transaction(
                user,
                account,
                category,
                requestDTO.type(),
                requestDTO.amount(),
                requestDTO.occurredOn(),
                description
        );

        transaction = transactionRepository.saveAndFlush(transaction);

        return toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponseDTO> findAllTransactions(
            UUID authenticatedUserId,
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            UUID accountId,
            UUID categoryId,
            Pageable pageable
    ) {
        validateDateRange(startDate, endDate);

        if (accountId != null) {
            financialAccountRepository.findByIdAndUserId(accountId, authenticatedUserId)
                    .orElseThrow(FinancialAccountNotFoundException::new);
        }

        if (categoryId != null) {
            categoryRepository.findByIdAndUserId(categoryId, authenticatedUserId)
                    .orElseThrow(CategoryNotFoundException::new);
        }

        Page<Transaction> transactions = transactionRepository.findAll(
                TransactionSpecifications.withFilters(
                        authenticatedUserId,
                        startDate,
                        endDate,
                        type,
                        accountId,
                        categoryId
                ),
                pageable
        );

        return transactions.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponseDTO findTransactionById(UUID transactionId, UUID authenticatedUserId) {
        Transaction transaction = transactionRepository.findByIdAndUser_Id(transactionId, authenticatedUserId)
                .orElseThrow(TransactionNotFoundException::new);

        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponseDTO updateTransaction(UUID transactionId,
                                                    UUID authenticatedUserId,
                                                    UpdateTransactionRequestDTO requestDTO) {
        Transaction transaction = transactionRepository.findByIdAndUser_Id(transactionId, authenticatedUserId)
                .orElseThrow(TransactionNotFoundException::new);
        FinancialAccount account = financialAccountRepository.findByIdAndUserId(requestDTO.accountId(), authenticatedUserId)
                .orElseThrow(FinancialAccountNotFoundException::new);
        Category category = categoryRepository.findByIdAndUserId(requestDTO.categoryId(), authenticatedUserId)
                .orElseThrow(CategoryNotFoundException::new);

        if (requestDTO.type() != category.getTransactionType()) {
            throw new TransactionTypeMismatchException();
        }

        String description = requestDTO.description() == null ? null : requestDTO.description().trim();

        transaction.update(
                account,
                category,
                requestDTO.type(),
                requestDTO.amount(),
                requestDTO.occurredOn(),
                description);

        transaction = transactionRepository.saveAndFlush(transaction);

        return toResponse(transaction);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidTransactionDateRangeException();
        }
    }

    private TransactionResponseDTO toResponse(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getId(),
                transaction.getFinancialAccount().getId(),
                transaction.getCategory().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getOccurredOn(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
