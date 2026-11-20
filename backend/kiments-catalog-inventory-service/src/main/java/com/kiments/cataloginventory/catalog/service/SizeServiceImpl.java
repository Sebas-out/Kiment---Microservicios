package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.domain.Size;
import com.kiments.cataloginventory.catalog.dto.SizeDtos;
import com.kiments.cataloginventory.catalog.repository.SizeRepository;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.shared.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SizeServiceImpl implements SizeService {

    private final SizeRepository repository;

    public SizeServiceImpl(SizeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public SizeDtos.Response create(SizeDtos.Create request) {
        String trimmedName = request.name().trim();
        repository.findByNameIgnoreCase(trimmedName).ifPresent(s -> {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SIZE_NAME",
                    "Ya existe una talla registrada con el nombre '" + trimmedName + "'");
        });

        Size size = new Size(
                UUID.randomUUID(),
                trimmedName,
                request.sortOrder() != null ? request.sortOrder() : 0,
                request.enabled() != null ? request.enabled() : true
        );
        return SizeDtos.Response.from(repository.save(size));
    }

    @Override
    @Transactional
    public SizeDtos.Response update(UUID id, SizeDtos.Update request) {
        Size size = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SIZE_NOT_FOUND", "Talla no encontrada con id: " + id));

        String trimmedName = request.name().trim();
        repository.findByNameIgnoreCase(trimmedName).ifPresent(s -> {
            if (!s.getId().equals(id)) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SIZE_NAME",
                        "Ya existe otra talla registrada con el nombre '" + trimmedName + "'");
            }
        });

        size.update(trimmedName, request.sortOrder() != null ? request.sortOrder() : size.getSortOrder());
        return SizeDtos.Response.from(size);
    }

    @Override
    @Transactional
    public SizeDtos.Response setStatus(UUID id, boolean enabled) {
        Size size = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SIZE_NOT_FOUND", "Talla no encontrada con id: " + id));
        size.setEnabled(enabled);
        return SizeDtos.Response.from(size);
    }

    @Override
    @Transactional(readOnly = true)
    public SizeDtos.Response getById(UUID id) {
        return repository.findById(id)
                .map(SizeDtos.Response::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SIZE_NOT_FOUND", "Talla no encontrada con id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SizeDtos.Response> list(String query, Boolean enabled, int page, int size) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("sortOrder").ascending().and(Sort.by("name").ascending()));
        String pattern = (query != null && !query.isBlank()) ? "%" + query.trim().toLowerCase() + "%" : null;
        var result = repository.findAllFiltered(pattern, enabled, pageable);
        return PageResponse.from(result.map(SizeDtos.Response::from));
    }
}
