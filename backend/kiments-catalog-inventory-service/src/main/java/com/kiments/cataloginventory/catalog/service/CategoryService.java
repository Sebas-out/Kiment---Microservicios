package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.dto.CategoryDtos;
import com.kiments.cataloginventory.shared.PageResponse;

import java.util.UUID;

public interface CategoryService {
    CategoryDtos.Response create(CategoryDtos.Create request);
    CategoryDtos.Response update(UUID id, CategoryDtos.Update request);
    CategoryDtos.Response setStatus(UUID id, boolean enabled);
    CategoryDtos.Response getById(UUID id);
    PageResponse<CategoryDtos.Response> list(String query, Boolean enabled, int page, int size);
}
