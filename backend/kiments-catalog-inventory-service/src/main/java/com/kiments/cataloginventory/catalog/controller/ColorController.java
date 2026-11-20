package com.kiments.cataloginventory.catalog.controller;

import com.kiments.cataloginventory.catalog.dto.ColorDtos;
import com.kiments.cataloginventory.catalog.service.ColorService;
import com.kiments.cataloginventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/colors")
@Validated
@Tag(name = "Colores", description = "Gestión de colores del catálogo")
public class ColorController {

    private final ColorService service;

    public ColorController(ColorService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Crear nuevo color")
    public ResponseEntity<ColorDtos.Response> create(@Valid @RequestBody ColorDtos.Create request) {
        ColorDtos.Response created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/colors/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Actualizar color existente")
    public ResponseEntity<ColorDtos.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody ColorDtos.Update request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Activar o desactivar color")
    public ResponseEntity<ColorDtos.Response> setStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ColorDtos.Status request) {
        return ResponseEntity.ok(service.setStatus(id, request.enabled()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ') or hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Obtener color por ID")
    public ResponseEntity<ColorDtos.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ') or hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Listar colores con paginación y filtros")
    public ResponseEntity<PageResponse<ColorDtos.Response>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(query, enabled, page, size));
    }
}
