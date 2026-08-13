package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.category.entity.Category;
import com.rodrigs.finance_manager_api.category.repository.CategoryRepository;
import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.shared.exception.CategoryNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.TransactionTypeMismatchException;
import com.rodrigs.finance_manager_api.shared.exception.UserNotFoundException;
import com.rodrigs.finance_manager_api.transaction.dto.CreateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
