package com.rodrigs.finance_manager_api.financial_account.service;

import com.rodrigs.finance_manager_api.financial_account.dto.CreateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.FinancialAccountResponseDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.UpdateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountHasTransactionsException;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.UserNotFoundException;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialAccountService {

    private final FinancialAccountRepository financialAccountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public FinancialAccountService(FinancialAccountRepository financialAccountRepository, UserRepository userRepository, TransactionRepository transactionRepository) {
        this.financialAccountRepository = financialAccountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public FinancialAccountResponseDTO createFinancialAccount(
            UUID authenticatedUserId, // ID vindo do JWT
            CreateFinancialAccountRequestDTO requestDTO // dados enviados no corpo da requisicao
    ) {
        // busca o usuario pelo ID
        User user = userRepository.findById(authenticatedUserId).orElseThrow(UserNotFoundException::new);

        // cria uma nova conta tratando parametros para equivalencia
        FinancialAccount account = new FinancialAccount(
                user,
                requestDTO.name().trim(),
                requestDTO.type(),
                requestDTO.initialBalance()
        );

        // salva a conta no banco
        account = financialAccountRepository.saveAndFlush(account);

        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<FinancialAccountResponseDTO> findAllAccounts(UUID authenticatedUserId) {
        // busca contas do user
        List<FinancialAccount> accounts = financialAccountRepository.findAllByUserIdOrderByCreatedAtAsc(authenticatedUserId);

        // cria lista de resposta
        List<FinancialAccountResponseDTO> response = new ArrayList<>();

        // percorre as contas
        for (FinancialAccount account : accounts) {
            // converte entidade em DTO e adiciona na lista de resposta
            response.add(toResponse(account));
        }

        // retorna lista de DTOs
        return response;
    }

    @Transactional(readOnly = true)
    public FinancialAccountResponseDTO findAccountById(UUID accountId, UUID authenticatedUserId) {
        // buscar por id + userId
        FinancialAccount account = financialAccountRepository.findByIdAndUserId(accountId, authenticatedUserId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        return toResponse(account);
    }

    @Transactional
    public FinancialAccountResponseDTO updateFinancialAccount(UUID accountId, UUID authenticatedUserId, UpdateFinancialAccountRequestDTO requestDTO) {
        FinancialAccount account = financialAccountRepository.findByIdAndUserId(accountId, authenticatedUserId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        boolean initialBalanceChanged = initialBalanceChanged(account, requestDTO);

        // verifica se o saldo mudou e se ha transacoes na conta
        if (initialBalanceChanged && transactionRepository.existsByFinancialAccount_IdAndUser_Id(accountId, authenticatedUserId)) {
            throw new FinancialAccountHasTransactionsException();
        }

        account.update(requestDTO.name().trim(), requestDTO.type(), requestDTO.initialBalance());

        return toResponse(account);
    }

    @Transactional
    public void deleteFinancialAccount(UUID accountId, UUID authenticatedUserId) {
        FinancialAccount account = financialAccountRepository.findByIdAndUserId(accountId, authenticatedUserId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        // se ha transacoes na conta
        if (transactionRepository.existsByFinancialAccount_IdAndUser_Id(accountId, authenticatedUserId)) {
            throw new FinancialAccountHasTransactionsException();
        }

        financialAccountRepository.delete(account);
    }

    // metodo para retornar uma conta por meio do DTO
    private FinancialAccountResponseDTO toResponse(FinancialAccount account) {
        return new FinancialAccountResponseDTO(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    // metodo para verificar se o saldo de uma conta mudou
    private boolean initialBalanceChanged(FinancialAccount account, UpdateFinancialAccountRequestDTO requestDTO) {
        return account.getInitialBalance().compareTo(requestDTO.initialBalance()) != 0;
    }


}
