package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.report.dto.CurrentBalanceResponseDTO;
import com.rodrigs.finance_manager_api.report.dto.ReportSummaryResponseDTO;
import com.rodrigs.finance_manager_api.report.repository.AccountBalanceProjection;
import com.rodrigs.finance_manager_api.report.repository.ReportTotalsProjection;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.InvalidTransactionDateRangeException;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private ReportTotalsProjection reportTotalsProjection;

    @Mock
    private AccountBalanceProjection firstAccountProjection;

    @Mock
    private AccountBalanceProjection secondAccountProjection;

    private ReportService reportService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(transactionRepository, financialAccountRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void shouldCalculateSummaryForPeriod() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(transactionRepository.findTotalsByUserAndPeriod(
                userId,
                startDate,
                endDate,
                TransactionType.INCOME,
                TransactionType.EXPENSE
        )).thenReturn(reportTotalsProjection);
        when(reportTotalsProjection.getTotalIncome()).thenReturn(new BigDecimal("1600.00"));
        when(reportTotalsProjection.getTotalExpense()).thenReturn(new BigDecimal("100.00"));

        ReportSummaryResponseDTO response = reportService.getSummary(userId, startDate, endDate);

        assertThat(response.startDate()).isEqualTo(startDate);
        assertThat(response.endDate()).isEqualTo(endDate);
        assertThat(response.totalIncome()).isEqualByComparingTo("1600.00");
        assertThat(response.totalExpense()).isEqualByComparingTo("100.00");
        assertThat(response.netBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void shouldReturnZeroSummaryWhenPeriodHasNoTransactions() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(transactionRepository.findTotalsByUserAndPeriod(
                userId,
                startDate,
                endDate,
                TransactionType.INCOME,
                TransactionType.EXPENSE
        )).thenReturn(reportTotalsProjection);
        when(reportTotalsProjection.getTotalIncome()).thenReturn(BigDecimal.ZERO);
        when(reportTotalsProjection.getTotalExpense()).thenReturn(BigDecimal.ZERO);

        ReportSummaryResponseDTO response = reportService.getSummary(userId, startDate, endDate);

        assertThat(response.totalIncome()).isEqualByComparingTo("0.00");
        assertThat(response.totalExpense()).isEqualByComparingTo("0.00");
        assertThat(response.netBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldReturnNegativeNetBalanceWhenExpensesAreGreaterThanIncome() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);

        when(transactionRepository.findTotalsByUserAndPeriod(
                userId,
                startDate,
                endDate,
                TransactionType.INCOME,
                TransactionType.EXPENSE
        )).thenReturn(reportTotalsProjection);
        when(reportTotalsProjection.getTotalIncome()).thenReturn(new BigDecimal("50.00"));
        when(reportTotalsProjection.getTotalExpense()).thenReturn(new BigDecimal("100.00"));

        ReportSummaryResponseDTO response = reportService.getSummary(userId, startDate, endDate);

        assertThat(response.netBalance()).isEqualByComparingTo("-50.00");
    }

    @Test
    void shouldRejectSummaryWithInvalidDateRange() {
        LocalDate startDate = LocalDate.of(2026, 8, 31);
        LocalDate endDate = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> reportService.getSummary(userId, startDate, endDate))
                .isInstanceOf(InvalidTransactionDateRangeException.class);

        verify(transactionRepository, never()).findTotalsByUserAndPeriod(
                eq(userId),
                eq(startDate),
                eq(endDate),
                eq(TransactionType.INCOME),
                eq(TransactionType.EXPENSE)
        );
    }

    @Test
    void shouldCalculateCurrentBalanceForAllAccounts() {
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        when(financialAccountRepository.findCurrentBalancesByUserId(
                userId,
                TransactionType.INCOME,
                TransactionType.EXPENSE
        )).thenReturn(List.of(firstAccountProjection, secondAccountProjection));
        when(firstAccountProjection.getAccountId()).thenReturn(firstAccountId);
        when(firstAccountProjection.getAccountName()).thenReturn("Bradesco");
        when(firstAccountProjection.getBalance()).thenReturn(new BigDecimal("2000.00"));
        when(secondAccountProjection.getAccountId()).thenReturn(secondAccountId);
        when(secondAccountProjection.getAccountName()).thenReturn("Nubank");
        when(secondAccountProjection.getBalance()).thenReturn(new BigDecimal("777.77"));

        CurrentBalanceResponseDTO response = reportService.getCurrentBalance(userId);

        assertThat(response.totalBalance()).isEqualByComparingTo("2777.77");
        assertThat(response.accounts()).hasSize(2);
        assertThat(response.accounts())
                .extracting(account -> account.accountName())
                .containsExactly("Bradesco", "Nubank");
        assertThat(response.accounts().get(0).balance()).isEqualByComparingTo("2000.00");
        assertThat(response.accounts().get(1).balance()).isEqualByComparingTo("777.77");
    }

    @Test
    void shouldReturnZeroWhenUserHasNoAccounts() {
        when(financialAccountRepository.findCurrentBalancesByUserId(
                userId,
                TransactionType.INCOME,
                TransactionType.EXPENSE
        )).thenReturn(List.of());

        CurrentBalanceResponseDTO response = reportService.getCurrentBalance(userId);

        assertThat(response.totalBalance()).isEqualByComparingTo("0.00");
        assertThat(response.accounts()).isEmpty();
    }
}
