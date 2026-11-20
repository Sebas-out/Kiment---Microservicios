package com.kiments.cataloginventory.catalog.dto;

import com.kiments.cataloginventory.catalog.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class CategoryDtos {
    private CategoryDtos() {}

    public record Create(
            @NotBlank(message = "El nombre de la categoría es obligatorio")
            @Size(max = 80, message = "El nombre no puede exceder 80 caracteres")
            String name,

            @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
            String description,

            Boolean enabled
    ) {}

    public record Update(
            @NotBlank(message = "El nombre de la categoría es obligatorio")
            @Size(max = 80, message = "El nombre no puede exceder 80 caracteres")
            String name,

            @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
            String description
    ) {}

    public record Status(boolean enabled) {}

    public record Response(
            UUID id,
            String name,
            String description,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        public static Response from(Category c) {
            return new Response(
                    c.getId(),
                    c.getName(),
                    c.getDescription(),
                    c.isEnabled(),
                    c.getCreatedAt(),
                    c.getUpdatedAt(),
                    c.getVersion()
            );
        }
    }
}
