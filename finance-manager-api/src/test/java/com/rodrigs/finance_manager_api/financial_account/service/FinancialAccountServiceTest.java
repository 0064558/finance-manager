package com.rodrigs.finance_manager_api.financial_account.service;

import com.rodrigs.finance_manager_api.financial_account.dto.CreateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.FinancialAccountResponseDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.UpdateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;
import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountHasTransactionsException;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountNotFoundException;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAccountServiceTest {

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private FinancialAccountService financialAccountService;
    private User user;

    @BeforeEach
    void setUp() {
        financialAccountService = new FinancialAccountService(
                financialAccountRepository,
                userRepository,
                transactionRepository
        );
        user = userWithId();
    }

    @Test
    void shouldCreateAccountForAuthenticatedUser() {
        CreateFinancialAccountRequestDTO request = new CreateFinancialAccountRequestDTO(
                "  Conta principal  ",
                AccountType.CHECKING,
                new BigDecimal("100.00")
        );
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(financialAccountRepository.saveAndFlush(any(FinancialAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FinancialAccountResponseDTO response = financialAccountService.createFinancialAccount(user.getId(), request);

        ArgumentCaptor<FinancialAccount> accountCaptor = ArgumentCaptor.forClass(FinancialAccount.class);
        verify(financialAccountRepository).saveAndFlush(accountCaptor.capture());

        FinancialAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getUser()).isEqualTo(user);
        assertThat(savedAccount.getName()).isEqualTo("Conta principal");
        assertThat(response.type()).isEqualTo(AccountType.CHECKING);
        assertThat(response.initialBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldReturnOnlyAccountsProvidedByRepository() {
        FinancialAccount firstAccount = account(user, "Conta 1", AccountType.CASH, "50.00");
        FinancialAccount secondAccount = account(user, "Conta 2", AccountType.SAVINGS, "200.00");
        when(financialAccountRepository.findAllByUserIdOrderByCreatedAtAsc(user.getId()))
                .thenReturn(List.of(firstAccount, secondAccount));

        List<FinancialAccountResponseDTO> response = financialAccountService.findAllAccounts(user.getId());

        assertThat(response).extracting(FinancialAccountResponseDTO::name)
                .containsExactly("Conta 1", "Conta 2");
        verify(financialAccountRepository).findAllByUserIdOrderByCreatedAtAsc(user.getId());
    }

    @Test
    void shouldNotFindAccountOwnedByAnotherUser() {
        UUID accountId = UUID.randomUUID();
        when(financialAccountRepository.findByIdAndUserId(accountId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> financialAccountService.findAccountById(accountId, user.getId()))
                .isInstanceOf(FinancialAccountNotFoundException.class);
    }

    @Test
    void shouldUpdateAccountWhenItHasNoTransactions() {
        FinancialAccount account = account(user, "Conta antiga", AccountType.CASH, "100.00");
        UpdateFinancialAccountRequestDTO request = new UpdateFinancialAccountRequestDTO(
                "  Conta nova  ",
                AccountType.CHECKING,
                new BigDecimal("150.00")
        );
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndUserId(account.getId(), user.getId()))
                .thenReturn(false);

        FinancialAccountResponseDTO response = financialAccountService.updateFinancialAccount(
                account.getId(), user.getId(), request
        );

        assertThat(response.name()).isEqualTo("Conta nova");
        assertThat(response.type()).isEqualTo(AccountType.CHECKING);
        assertThat(response.initialBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void shouldRejectInitialBalanceChangeWhenAccountHasTransactions() {
        FinancialAccount account = account(user, "Conta", AccountType.CASH, "100.00");
        UpdateFinancialAccountRequestDTO request = new UpdateFinancialAccountRequestDTO(
                "Conta atualizada",
                AccountType.CASH,
                new BigDecimal("150.00")
        );
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndUserId(account.getId(), user.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> financialAccountService.updateFinancialAccount(
                account.getId(), user.getId(), request
        )).isInstanceOf(FinancialAccountHasTransactionsException.class);

        assertThat(account.getName()).isEqualTo("Conta");
        verify(transactionRepository).existsByAccountIdAndUserId(account.getId(), user.getId());
    }

    @Test
    void shouldDeleteAccountWhenItHasNoTransactions() {
        FinancialAccount account = account(user, "Conta", AccountType.CASH, "100.00");
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndUserId(account.getId(), user.getId()))
                .thenReturn(false);

        financialAccountService.deleteFinancialAccount(account.getId(), user.getId());

        verify(financialAccountRepository).delete(account);
    }

    @Test
    void shouldRejectDeleteWhenAccountHasTransactions() {
        FinancialAccount account = account(user, "Conta", AccountType.CASH, "100.00");
        when(financialAccountRepository.findByIdAndUserId(account.getId(), user.getId()))
                .thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountIdAndUserId(account.getId(), user.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> financialAccountService.deleteFinancialAccount(account.getId(), user.getId()))
                .isInstanceOf(FinancialAccountHasTransactionsException.class);

        verify(financialAccountRepository, never()).delete(any(FinancialAccount.class));
    }

    private FinancialAccount account(User owner, String name, AccountType type, String balance) {
        FinancialAccount account = new FinancialAccount(owner, name, type, new BigDecimal(balance));
        setId(account, UUID.randomUUID());
        return account;
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
