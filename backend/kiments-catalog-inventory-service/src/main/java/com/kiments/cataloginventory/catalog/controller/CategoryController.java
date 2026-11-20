package com.kiments.cataloginventory.catalog.controller;

import com.kiments.cataloginventory.catalog.dto.CategoryDtos;
import com.kiments.cataloginventory.catalog.service.CategoryService;
import com.kiments.cataloginventory.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@Validated
@Tag(name = "Categorías", description = "Gestión de categorías del catálogo")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    @Operation(summary = "Crear nueva categoría")
    public ResponseEntity<CategoryDtos.Response> create(@Valid @RequestBody CategoryDtos.Create request) {
        CategoryDtos.Response created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    @Operation(summary = "Actualizar categoría existente")
    public ResponseEntity<CategoryDtos.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryDtos.Update request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    @Operation(summary = "Activar o desactivar categoría")
    public ResponseEntity<CategoryDtos.Response> setStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryDtos.Status request) {
        return ResponseEntity.ok(service.setStatus(id, request.enabled()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ') or hasAuthority('CATEGORY_WRITE')")
    @Operation(summary = "Obtener categoría por ID")
    public ResponseEntity<CategoryDtos.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ') or hasAuthority('CATEGORY_WRITE')")
    @Operation(summary = "Listar categorías con paginación y filtros")
    public ResponseEntity<PageResponse<CategoryDtos.Response>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(query, enabled, page, size));
    }
}
