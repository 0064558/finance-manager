package com.rodrigs.finance_manager_api.transaction.repository.specification;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.transaction.entity.Transaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> withFilters(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate,
            TransactionType type,
            UUID accountId,
            UUID categoryId
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(
                    root.get("user").get("id"),
                    userId
            ));

            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.<LocalDate>get("occurredOn"),
                        startDate
                ));
            }

            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.<LocalDate>get("occurredOn"),
                        endDate
                ));
            }

            if (type != null) {
                predicates.add(criteriaBuilder.equal(
                        root.<TransactionType>get("type"),
                        type
                ));
            }

            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("financialAccount").get("id"),
                        accountId
                ));
            }

            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("category").get("id"),
                        categoryId
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
