package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.dto.SizeDtos;
import com.kiments.cataloginventory.shared.PageResponse;

import java.util.UUID;

public interface SizeService {
    SizeDtos.Response create(SizeDtos.Create request);
    SizeDtos.Response update(UUID id, SizeDtos.Update request);
    SizeDtos.Response setStatus(UUID id, boolean enabled);
    SizeDtos.Response getById(UUID id);
    PageResponse<SizeDtos.Response> list(String query, Boolean enabled, int page, int size);
}
