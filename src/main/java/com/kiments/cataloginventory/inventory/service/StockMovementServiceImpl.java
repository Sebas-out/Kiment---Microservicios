package com.kiments.cataloginventory.inventory.service;

import com.kiments.cataloginventory.inventory.domain.StockMovement;
import com.kiments.cataloginventory.inventory.domain.StockOperationType;
import com.kiments.cataloginventory.inventory.dto.InventoryDtos;
import com.kiments.cataloginventory.inventory.repository.StockMovementRepository;
import com.kiments.cataloginventory.inventory.repository.StockMovementSpecifications;
import com.kiments.cataloginventory.security.CurrentUser;
import com.kiments.cataloginventory.shared.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository repository;

    public StockMovementServiceImpl(StockMovementRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryDtos.StockMovementResponse> listMovements(
            UUID requestedBranchId,
            StockOperationType type,
            UUID variantId,
            Instant from,
            Instant to,
            String query,
            int page,
            int size,
            CurrentUser user) {

        UUID branchScope = user.isAdmin() ? requestedBranchId : user.branchId();
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("operation.occurredAt").descending().and(Sort.by("id").ascending()));

        var spec = StockMovementSpecifications.filter(branchScope, type, variantId, from, to, query);
        Page<StockMovement> pageResult = repository.findAll(spec, pageable);

        return PageResponse.from(pageResult.map(this::mapToMovementResponse));
    }

    private InventoryDtos.StockMovementResponse mapToMovementResponse(StockMovement m) {
        return new InventoryDtos.StockMovementResponse(
                m.getId(),
                m.getOperation().getId(),
                m.getOperation().getType(),
                m.getVariant().getId(),
                m.getBranchId(),
                m.getBranchName(),
                m.getDestinationBranchName(),
                m.getProductName(),
                m.getVariantDetail(),
                m.getBeforeQuantity(),
                m.getDelta(),
                m.getAfterQuantity(),
                m.getOperation().getReason(),
                m.getUserName(),
                m.getOperation().getActorUserId(),
                m.getOperation().getOccurredAt(),
                m.getOperation().getCorrelationId()
        );
    }
}
