package com.kiments.cataloginventory.inventory.repository;

import com.kiments.cataloginventory.inventory.domain.StockTransfer;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StockTransferSpecifications {

    private StockTransferSpecifications() {}

    public static Specification<StockTransfer> filter(
            UUID branchScope,
            Instant from,
            Instant to) {

        return (root, cq, cb) -> {
            Class<?> resultType = cq.getResultType();
            boolean isCount = Long.class.equals(resultType) || long.class.equals(resultType);
            if (!isCount) {
                root.fetch("operation", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (branchScope != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("originBranchId"), branchScope),
                        cb.equal(root.get("destinationBranchId"), branchScope)
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
