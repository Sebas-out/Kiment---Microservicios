package com.kiments.cataloginventory.catalog.controller;

import com.kiments.cataloginventory.catalog.dto.SizeDtos;
import com.kiments.cataloginventory.catalog.service.SizeService;
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
@RequestMapping("/api/v1/sizes")
@Validated
@Tag(name = "Tallas", description = "Gestión de tallas del catálogo")
public class SizeController {

    private final SizeService service;

    public SizeController(SizeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Crear nueva talla")
    public ResponseEntity<SizeDtos.Response> create(@Valid @RequestBody SizeDtos.Create request) {
        SizeDtos.Response created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/sizes/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Actualizar talla existente")
    public ResponseEntity<SizeDtos.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody SizeDtos.Update request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Activar o desactivar talla")
    public ResponseEntity<SizeDtos.Response> setStatus(
            @PathVariable UUID id,
            @Valid @RequestBody SizeDtos.Status request) {
        return ResponseEntity.ok(service.setStatus(id, request.enabled()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ') or hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Obtener talla por ID")
    public ResponseEntity<SizeDtos.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ') or hasAuthority('VARIANT_WRITE')")
    @Operation(summary = "Listar tallas con paginación y orden")
    public ResponseEntity<PageResponse<SizeDtos.Response>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(query, enabled, page, size));
    }
}
