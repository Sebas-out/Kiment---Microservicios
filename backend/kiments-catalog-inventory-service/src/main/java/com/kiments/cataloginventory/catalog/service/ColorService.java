package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.dto.ColorDtos;
import com.kiments.cataloginventory.shared.PageResponse;

import java.util.UUID;

public interface ColorService {
    ColorDtos.Response create(ColorDtos.Create request);
    ColorDtos.Response update(UUID id, ColorDtos.Update request);
    ColorDtos.Response setStatus(UUID id, boolean enabled);
    ColorDtos.Response getById(UUID id);
    PageResponse<ColorDtos.Response> list(String query, Boolean enabled, int page, int size);
}
