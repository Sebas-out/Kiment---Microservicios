package com.kiments.cataloginventory.catalog.service;

import com.kiments.cataloginventory.catalog.domain.Color;
import com.kiments.cataloginventory.catalog.dto.ColorDtos;
import com.kiments.cataloginventory.catalog.repository.ColorRepository;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.shared.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ColorServiceImpl implements ColorService {

    private final ColorRepository repository;

    public ColorServiceImpl(ColorRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ColorDtos.Response create(ColorDtos.Create request) {
        String trimmedName = request.name().trim();
        repository.findByNameIgnoreCase(trimmedName).ifPresent(c -> {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_COLOR_NAME",
                    "Ya existe un color registrado con el nombre '" + trimmedName + "'");
        });

        Color color = new Color(
                UUID.randomUUID(),
                trimmedName,
                request.hex(),
                request.enabled() != null ? request.enabled() : true
        );
        return ColorDtos.Response.from(repository.save(color));
    }

    @Override
    @Transactional
    public ColorDtos.Response update(UUID id, ColorDtos.Update request) {
        Color color = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COLOR_NOT_FOUND", "Color no encontrado con id: " + id));

        String trimmedName = request.name().trim();
        repository.findByNameIgnoreCase(trimmedName).ifPresent(c -> {
            if (!c.getId().equals(id)) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_COLOR_NAME",
                        "Ya existe otro color registrado con el nombre '" + trimmedName + "'");
            }
        });

        color.update(trimmedName, request.hex());
        return ColorDtos.Response.from(color);
    }

    @Override
    @Transactional
    public ColorDtos.Response setStatus(UUID id, boolean enabled) {
        Color color = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COLOR_NOT_FOUND", "Color no encontrado con id: " + id));
        color.setEnabled(enabled);
        return ColorDtos.Response.from(color);
    }

    @Override
    @Transactional(readOnly = true)
    public ColorDtos.Response getById(UUID id) {
        return repository.findById(id)
                .map(ColorDtos.Response::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COLOR_NOT_FOUND", "Color no encontrado con id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ColorDtos.Response> list(String query, Boolean enabled, int page, int size) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.min(100, Math.max(1, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by("name").ascending().and(Sort.by("id").ascending()));
        String pattern = (query != null && !query.isBlank()) ? "%" + query.trim().toLowerCase() + "%" : null;
        var result = repository.findAllFiltered(pattern, enabled, pageable);
        return PageResponse.from(result.map(ColorDtos.Response::from));
    }
}
