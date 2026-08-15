package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.report.dto.ReportSummaryResponseDTO;
import com.rodrigs.finance_manager_api.report.repository.ReportTotalsProjection;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.InvalidTransactionDateRangeException;
import com.rodrigs.finance_manager_api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ReportService {

    private final TransactionRepository transactionRepository;

    public ReportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
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

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidTransactionDateRangeException();
        }
    }

}
