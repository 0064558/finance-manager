package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.category.entity.Category;
import com.rodrigs.finance_manager_api.category.repository.CategoryRepository;
import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;
import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.CategoryNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.InvalidTransactionDateRangeException;
import com.rodrigs.finance_manager_api.shared.exception.TransactionNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.TransactionTypeMismatchException;
import com.rodrigs.finance_manager_api.shared.exception.UserNotFoundException;
import com.rodrigs.finance_manager_api.transaction.dto.CreateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.dto.UpdateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private TransactionService transactionService;
    private User user;
    private FinancialAccount account;
    private Category category;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                userRepository,
                financialAccountRepository,
                categoryRepository
        );
        user = userWithId();
        account = account(user, "Conta principal");
        category = category(user, "Alimentação", TransactionType.EXPENSE);
    }

    @Test
    void shouldCreateTransactionForAuthenticatedUser() {
        CreateTransactionRequestDTO request = new CreateTransactionRequestDTO(
                account.getId(),
                category.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 8, 13),
                "  Mercado  "
        );

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndUserId(category.getId(), user.getId()))
                .thenReturn(Optional.of(category));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    setId(transaction, UUID.randomUUID());
                    return transaction;
                });

        TransactionResponseDTO response = transactionService.createTransaction(user.getId(), request);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).saveAndFlush(transactionCaptor.capture());

        Transaction savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getUser()).isEqualTo(user);
        assertThat(savedTransaction.getFinancialAccount()).isEqualTo(account);
        assertThat(savedTransaction.getCategory()).isEqualTo(category);
        assertThat(savedTransaction.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo("100.00");
        assertThat(savedTransaction.getOccurredOn()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(savedTransaction.getDescription()).isEqualTo("Mercado");
        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(response.categoryId()).isEqualTo(category.getId());
    }

    @Test
    void shouldRejectCreationWhenAuthenticatedUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        CreateTransactionRequestDTO request = createRequest(account.getId(), category.getId());

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(userId, request))
                .isInstanceOf(UserNotFoundException.class);

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldRejectCreationWhenAccountDoesNotBelongToUser() {
        CreateTransactionRequestDTO request = createRequest(account.getId(), category.getId());

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(user.getId(), request))
                .isInstanceOf(FinancialAccountNotFoundException.class);

        verify(categoryRepository, never()).findByIdAndUserId(any(UUID.class), any(UUID.class));
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldRejectCreationWhenCategoryDoesNotBelongToUser() {
        CreateTransactionRequestDTO request = createRequest(account.getId(), category.getId());

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndUserId(category.getId(), user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(user.getId(), request))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldRejectCreationWhenTransactionTypeDoesNotMatchCategory() {
        CreateTransactionRequestDTO request = new CreateTransactionRequestDTO(
                account.getId(),
                category.getId(),
                TransactionType.INCOME,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 8, 13),
                "Salário"
        );

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndUserId(category.getId(), user.getId()))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> transactionService.createTransaction(user.getId(), request))
                .isInstanceOf(TransactionTypeMismatchException.class);

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldFindTransactionOwnedByAuthenticatedUser() {
        Transaction transaction = transaction(user, account, category, "Mercado");

        when(transactionRepository.findByIdAndUser_Id(transaction.getId(), user.getId()))
                .thenReturn(Optional.of(transaction));

        TransactionResponseDTO response = transactionService.findTransactionById(
                transaction.getId(),
                user.getId()
        );

        assertThat(response.id()).isEqualTo(transaction.getId());
        assertThat(response.accountId()).isEqualTo(account.getId());
        assertThat(response.categoryId()).isEqualTo(category.getId());
        assertThat(response.description()).isEqualTo("Mercado");
    }

    @Test
    void shouldNotFindTransactionOwnedByAnotherUser() {
        UUID transactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndUser_Id(transactionId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findTransactionById(transactionId, user.getId()))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void shouldListTransactionsUsingAuthenticatedUserAndPageable() {
        Transaction first = transaction(user, account, category, "Mercado");
        Transaction second = transaction(user, account, category, "Farmácia");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Transaction> transactionPage = new PageImpl<>(List.of(first, second), pageable, 2);

        when(transactionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(transactionPage);

        Page<TransactionResponseDTO> response = transactionService.findAllTransactions(
                user.getId(),
                null,
                null,
                null,
                null,
                null,
                pageable
        );

        assertThat(response.getContent())
                .extracting(TransactionResponseDTO::description)
                .containsExactly("Mercado", "Farmácia");
        assertThat(response.getTotalElements()).isEqualTo(2);
        verify(transactionRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void shouldRejectInvalidTransactionDateRange() {
        LocalDate startDate = LocalDate.of(2026, 8, 31);
        LocalDate endDate = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> transactionService.findAllTransactions(
                user.getId(),
                startDate,
                endDate,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        )).isInstanceOf(InvalidTransactionDateRangeException.class);

        verify(transactionRepository, never())
                .findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldRejectAccountFilterWhenAccountDoesNotBelongToUser() {
        UUID accountId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        when(financialAccountRepository.findByIdAndUserId(accountId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.findAllTransactions(
                user.getId(),
                null,
                null,
                null,
                accountId,
                null,
                pageable
        )).isInstanceOf(FinancialAccountNotFoundException.class);

        verify(transactionRepository, never())
                .findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldUpdateTransactionAndNormalizeDescription() {
        Transaction transaction = transaction(user, account, category, "Mercado");
        UpdateTransactionRequestDTO request = new UpdateTransactionRequestDTO(
                account.getId(),
                category.getId(),
                TransactionType.EXPENSE,
                new BigDecimal("150.75"),
                LocalDate.of(2026, 8, 14),
                "  Mercado atualizado  "
        );

        when(transactionRepository.findByIdAndUser_Id(transaction.getId(), user.getId()))
                .thenReturn(Optional.of(transaction));
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndUserId(category.getId(), user.getId()))
                .thenReturn(Optional.of(category));
        when(transactionRepository.saveAndFlush(transaction)).thenReturn(transaction);

        TransactionResponseDTO response = transactionService.updateTransaction(
                transaction.getId(),
                user.getId(),
                request
        );

        assertThat(transaction.getAmount()).isEqualByComparingTo("150.75");
        assertThat(transaction.getOccurredOn()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(transaction.getDescription()).isEqualTo("Mercado atualizado");
        assertThat(response.amount()).isEqualByComparingTo("150.75");
        verify(transactionRepository).saveAndFlush(transaction);
    }

    @Test
    void shouldRejectUpdateWhenTransactionTypeDoesNotMatchCategory() {
        Transaction transaction = transaction(user, account, category, "Mercado");
        UpdateTransactionRequestDTO request = new UpdateTransactionRequestDTO(
                account.getId(),
                category.getId(),
                TransactionType.INCOME,
                new BigDecimal("150.75"),
                LocalDate.of(2026, 8, 14),
                "Atualização"
        );

        when(transactionRepository.findByIdAndUser_Id(transaction.getId(), user.getId()))
                .thenReturn(Optional.of(transaction));
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndUserId(category.getId(), user.getId()))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> transactionService.updateTransaction(
                transaction.getId(),
                user.getId(),
                request
        )).isInstanceOf(TransactionTypeMismatchException.class);

        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldDeleteTransactionOwnedByAuthenticatedUser() {
        Transaction transaction = transaction(user, account, category, "Mercado");

        when(transactionRepository.findByIdAndUser_Id(transaction.getId(), user.getId()))
                .thenReturn(Optional.of(transaction));

        transactionService.deleteTransaction(transaction.getId(), user.getId());

        verify(transactionRepository).delete(transaction);
    }

    @Test
    void shouldNotDeleteTransactionOwnedByAnotherUser() {
        UUID transactionId = UUID.randomUUID();

        when(transactionRepository.findByIdAndUser_Id(transactionId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.deleteTransaction(transactionId, user.getId()))
                .isInstanceOf(TransactionNotFoundException.class);

        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    private CreateTransactionRequestDTO createRequest(UUID accountId, UUID categoryId) {
        return new CreateTransactionRequestDTO(
                accountId,
                categoryId,
                TransactionType.EXPENSE,
                new BigDecimal("100.00"),
                LocalDate.of(2026, 8, 13),
                "Mercado"
        );
    }

    private Transaction transaction(
            User owner,
            FinancialAccount financialAccount,
            Category transactionCategory,
            String description
    ) {
        Transaction transaction = new Transaction(
                owner,
                financialAccount,
                transactionCategory,
                transactionCategory.getTransactionType(),
                new BigDecimal("100.00"),
                LocalDate.of(2026, 8, 13),
                description
        );
        setId(transaction, UUID.randomUUID());
        return transaction;
    }

    private FinancialAccount account(User owner, String name) {
        FinancialAccount account = new FinancialAccount(
                owner,
                name,
                AccountType.CHECKING,
                new BigDecimal("1000.00")
        );
        setId(account, UUID.randomUUID());
        return account;
    }

    private Category category(User owner, String name, TransactionType type) {
        Category category = new Category(owner, name, type);
        setId(category, UUID.randomUUID());
        return category;
    }

    private User userWithId() {
        User user = new User("Rodrigo", "rodrigo@email.com", "hashed-password");
        setId(user, UUID.randomUUID());
        return user;
    }

    private void setId(Object entity, UUID id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not prepare test entity", exception);
        }
    }
}
