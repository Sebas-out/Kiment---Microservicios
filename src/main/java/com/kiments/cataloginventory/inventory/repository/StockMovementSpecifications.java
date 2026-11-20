package com.kiments.cataloginventory.inventory.repository;

import com.kiments.cataloginventory.inventory.domain.StockMovement;
import com.kiments.cataloginventory.inventory.domain.StockOperation;
import com.kiments.cataloginventory.inventory.domain.StockOperationType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StockMovementSpecifications {

    private StockMovementSpecifications() {}

    public static Specification<StockMovement> filter(
            UUID branchScope,
            StockOperationType type,
            UUID variantId,
            Instant from,
            Instant to,
            String query) {

        return (root, cq, cb) -> {
            Class<?> resultType = cq.getResultType();
            boolean isCount = Long.class.equals(resultType) || long.class.equals(resultType);
            if (!isCount) {
                root.fetch("operation", JoinType.LEFT);
                root.fetch("variant", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (branchScope != null) {
                predicates.add(cb.equal(root.get("branchId"), branchScope));
            }

            if (variantId != null) {
                predicates.add(cb.equal(root.get("variant").get("id"), variantId));
            }

            boolean needsOperationJoin = type != null || from != null || to != null || (query != null && !query.isBlank());
            if (needsOperationJoin) {
                Join<StockMovement, StockOperation> opJoin = root.join("operation", JoinType.LEFT);

                if (type != null) {
                    predicates.add(cb.equal(opJoin.get("type"), type));
                }
                if (from != null) {
                    predicates.add(cb.greaterThanOrEqualTo(opJoin.get("occurredAt"), from));
                }
                if (to != null) {
                    predicates.add(cb.lessThanOrEqualTo(opJoin.get("occurredAt"), to));
                }
                if (query != null && !query.isBlank()) {
                    String p = "%" + query.trim().toLowerCase() + "%";
                    Predicate productMatch = cb.like(cb.lower(root.get("productName")), p);
                    Predicate variantMatch = cb.like(cb.lower(root.get("variantDetail")), p);
                    Predicate userMatch = cb.like(cb.lower(root.get("userName")), p);
                    Predicate reasonMatch = cb.and(
                            cb.isNotNull(opJoin.get("reason")),
                            cb.like(cb.lower(opJoin.get("reason")), p)
                    );
                    predicates.add(cb.or(productMatch, variantMatch, userMatch, reasonMatch));
                }
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
