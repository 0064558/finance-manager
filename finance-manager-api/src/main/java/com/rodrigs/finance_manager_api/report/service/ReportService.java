package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.report.dto.AccountBalanceResponseDTO;
import com.rodrigs.finance_manager_api.report.dto.CurrentBalanceResponseDTO;
import com.rodrigs.finance_manager_api.report.dto.CashFlowPointResponseDTO;
import com.rodrigs.finance_manager_api.report.dto.CashFlowResponseDTO;
import com.rodrigs.finance_manager_api.report.dto.ReportSummaryResponseDTO;
import com.rodrigs.finance_manager_api.report.repository.AccountBalanceProjection;
import com.rodrigs.finance_manager_api.report.repository.CashFlowProjection;
import com.rodrigs.finance_manager_api.report.repository.ReportTotalsProjection;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.InvalidTransactionDateRangeException;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final FinancialAccountRepository financialAccountRepository;

    public ReportService(TransactionRepository transactionRepository, FinancialAccountRepository financialAccountRepository) {
        this.transactionRepository = transactionRepository;
        this.financialAccountRepository = financialAccountRepository;
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponseDTO getSummary(UUID authenticatedUserId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        ReportTotalsProjection totals = transactionRepository.findTotalsByUserAndPeriod(
                authenticatedUserId,
                startDate,
                endDate,
                TransactionType.INCOME,
                TransactionType.EXPENSE);

        BigDecimal netBalance = totals.getTotalIncome().subtract(totals.getTotalExpense());

        return new ReportSummaryResponseDTO(
                startDate,
                endDate,
                totals.getTotalIncome(),
                totals.getTotalExpense(),
                netBalance
        );
    }

    @Transactional(readOnly = true)
    public CurrentBalanceResponseDTO getCurrentBalance(UUID authenticatedUserId) {

        // busca os saldos das contas no banco
        List<AccountBalanceProjection> accountBalance = financialAccountRepository.findCurrentBalancesByUserId(
                authenticatedUserId,
                TransactionType.INCOME,
                TransactionType.EXPENSE
        );

        // lista para os dtos de reposta
        List<AccountBalanceResponseDTO> accountBalanceDtos = new ArrayList<>();
        // inicializar o total com zero
        BigDecimal totalBalance = BigDecimal.ZERO;

        // percorre cada conta
        for (AccountBalanceProjection acc : accountBalance) {
            // obtem o saldo da conta atual
            BigDecimal balance = acc.getBalance();
            // converte a projection em dto
            accountBalanceDtos.add(new AccountBalanceResponseDTO(
                    acc.getAccountId(),
                    acc.getAccountName(),
                    balance
            ));
            // soma o saldo ao total
            totalBalance = totalBalance.add(balance);
        }

        // retorna o total e a lista de contas
        return new CurrentBalanceResponseDTO(totalBalance, accountBalanceDtos);
    }

    @Transactional(readOnly = true)
    public CashFlowResponseDTO getCashFlow(UUID authenticatedUserId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        List<CashFlowProjection> groupedPoints = transactionRepository.findCashFlowByUserAndPeriod(
                authenticatedUserId,
                startDate,
                endDate,
                TransactionType.INCOME,
                TransactionType.EXPENSE
        );

        Map<LocalDate, CashFlowProjection> pointsByDate = groupedPoints.stream()
                .collect(Collectors.toMap(CashFlowProjection::getOccurredOn, Function.identity()));

        List<CashFlowPointResponseDTO> points = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> {
                    CashFlowProjection point = pointsByDate.get(date);
                    BigDecimal totalIncome = point == null ? BigDecimal.ZERO : point.getTotalIncome();
                    BigDecimal totalExpense = point == null ? BigDecimal.ZERO : point.getTotalExpense();

                    return new CashFlowPointResponseDTO(
                            date,
                            totalIncome,
                            totalExpense,
                            totalIncome.subtract(totalExpense)
                    );
                })
                .toList();

        return new CashFlowResponseDTO(startDate, endDate, points);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidTransactionDateRangeException();
        }
    }

}
