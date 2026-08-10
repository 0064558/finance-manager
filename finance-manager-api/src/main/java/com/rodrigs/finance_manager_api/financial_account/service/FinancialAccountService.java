package com.rodrigs.finance_manager_api.financial_account.service;

import com.rodrigs.finance_manager_api.financial_account.dto.CreateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.FinancialAccountResponseDTO;
import com.rodrigs.finance_manager_api.financial_account.entity.FinancialAccount;
import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.user.entity.User;
import com.rodrigs.finance_manager_api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FinancialAccountService {

    private final FinancialAccountRepository financialAccountRepository;
    private final UserRepository userRepository;

    public FinancialAccountService(FinancialAccountRepository financialAccountRepository, UserRepository userRepository) {
        this.financialAccountRepository = financialAccountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FinancialAccountResponseDTO createFinancialAccount(
            UUID authenticatedUserId, // ID vindo do JWT
            CreateFinancialAccountRequestDTO requestDTO // dados enviados no corpo da requisicao
    ) {
        // busca o usuario pelo ID
        User user = userRepository.findById(authenticatedUserId).orElseThrow();

        // cria uma nova conta tratando parametros para equivalencia
        FinancialAccount account = new FinancialAccount(
                user,
                requestDTO.name().trim(),
                requestDTO.type(),
                requestDTO.initialBalance()
        );

        // salva a conta no banco
        account = financialAccountRepository.saveAndFlush(account);

        return response(account);
    }

    // metodo para retornar uma conta por meio do DTO
    private FinancialAccountResponseDTO response(FinancialAccount account) {
        return new FinancialAccountResponseDTO(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
