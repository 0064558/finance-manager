package com.rodrigs.finance_manager_api.report.repository;

import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import com.rodrigs.finance_manager_api.transaction.repository.specification.TransactionSpecifications;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class ReportExportRepository {
    private final EntityManager entityManager;

    public ReportExportRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Transaction> findRows(UUID userId, ReportExportFilter filter, int limit) {
        var builder = entityManager.getCriteriaBuilder();
        var query = builder.createQuery(Transaction.class);
        var root = query.from(Transaction.class);
        // Only to-one joins: the database can enforce the row limit without in-memory pagination.
        root.fetch("financialAccount");
        root.fetch("category");
        query.select(root).where(TransactionSpecifications.withFilters(userId, filter.startDate(),
                filter.endDate(), filter.type(), filter.accountId(), filter.categoryId())
                .toPredicate(root, query, builder));
        query.orderBy(builder.asc(root.get("occurredOn")), builder.asc(root.get("id")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }
}
