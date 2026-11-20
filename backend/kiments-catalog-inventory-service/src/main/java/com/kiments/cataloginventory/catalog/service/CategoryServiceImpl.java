package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.domain.Category;
import com.kiments.cataloginventory.catalog.dto.CategoryDtos;
import com.kiments.cataloginventory.catalog.repository.CategoryRepository;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.shared.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repository;

    public CategoryServiceImpl(CategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public CategoryDtos.Response create(CategoryDtos.Create request) {
        String trimmedName = request.name().trim();
        repository.findByNameIgnoreCase(trimmedName).ifPresent(c -> {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CATEGORY_NAME",
                    "Ya existe una categoría registrada con el nombre '" + trimmedName + "'");
        });

        Category category = new Category(
                UUID.randomUUID(),
                trimmedName,
                request.description(),
                request.enabled() != null ? request.enabled() : true
        );
        return CategoryDtos.Response.from(repository.save(category));
    }

    @Override
    @Transactional
    public CategoryDtos.Response update(UUID id, CategoryDtos.Update request) {
        Category category = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Categoría no encontrada con id: " + id));

        String trimmedName = request.name().trim();
        repository.findByNameIgnoreCase(trimmedName).ifPresent(c -> {
            if (!c.getId().equals(id)) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CATEGORY_NAME",
                        "Ya existe otra categoría registrada con el nombre '" + trimmedName + "'");
            }
        });

        category.update(trimmedName, request.description());
        return CategoryDtos.Response.from(category);
    }

    @Override
    @Transactional
    public CategoryDtos.Response setStatus(UUID id, boolean enabled) {
        Category category = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Categoría no encontrada con id: " + id));
        category.setEnabled(enabled);
        return CategoryDtos.Response.from(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDtos.Response getById(UUID id) {
        return repository.findById(id)
                .map(CategoryDtos.Response::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Categoría no encontrada con id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryDtos.Response> list(String query, Boolean enabled, int page, int size) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("name").ascending().and(Sort.by("id").ascending()));
        String pattern = (query != null && !query.isBlank()) ? "%" + query.trim().toLowerCase() + "%" : null;
        var result = repository.findAllFiltered(pattern, enabled, pageable);
        return PageResponse.from(result.map(CategoryDtos.Response::from));
    }
}
