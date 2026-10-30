package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.category.repository.CategoryRepository;
import com.rodrigs.finance_manager_api.financial_account.repository.FinancialAccountRepository;
import com.rodrigs.finance_manager_api.report.dto.ReportExportData;
import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.report.dto.ReportExportRow;
import com.rodrigs.finance_manager_api.report.repository.ReportExportRepository;
import com.rodrigs.finance_manager_api.shared.exception.CategoryNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.FinancialAccountNotFoundException;
import com.rodrigs.finance_manager_api.shared.exception.InvalidTransactionDateRangeException;
import com.rodrigs.finance_manager_api.shared.exception.ReportExportLimitExceededException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ReportExportQueryService {
    public static final int MAX_DAYS = 366;
    public static final int MAX_ROWS = 5000;
    private final ReportExportRepository repository;
    private final FinancialAccountRepository accounts;
    private final CategoryRepository categories;

    public ReportExportQueryService(ReportExportRepository repository,
                                   FinancialAccountRepository accounts, CategoryRepository categories) {
        this.repository = repository;
        this.accounts = accounts;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public ReportExportData load(UUID userId, ReportExportFilter filter) {
        if (filter.startDate() == null || filter.endDate() == null || filter.startDate().isAfter(filter.endDate())) {
            throw new InvalidTransactionDateRangeException();
        }
        if (ChronoUnit.DAYS.between(filter.startDate(), filter.endDate()) + 1 > MAX_DAYS) {
            throw new ReportExportLimitExceededException();
        }
        if (filter.accountId() != null) {
            accounts.findByIdAndUserId(filter.accountId(), userId).orElseThrow(FinancialAccountNotFoundException::new);
        }
        if (filter.categoryId() != null) {
            categories.findByIdAndUserId(filter.categoryId(), userId).orElseThrow(CategoryNotFoundException::new);
        }
        var transactions = repository.findRows(userId, filter, MAX_ROWS + 1);
        if (transactions.size() > MAX_ROWS) {
            throw new ReportExportLimitExceededException();
        }
        var rows = transactions.stream().map(t -> new ReportExportRow(t.getOccurredOn(), t.getDescription(),
                t.getType(), t.getFinancialAccount().getName(), t.getCategory().getName(), t.getAmount())).toList();
        return new ReportExportData(filter, rows);
    }
}
