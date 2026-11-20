package com.kiments.cataloginventory.catalog.dto;

import com.kiments.cataloginventory.catalog.domain.Size;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class SizeDtos {
    private SizeDtos() {}

    public record Create(
            @NotBlank(message = "El nombre de la talla es obligatorio")
            @jakarta.validation.constraints.Size(max = 30, message = "El nombre no puede exceder 30 caracteres")
            String name,

            Integer sortOrder,
            Boolean enabled
    ) {}

    public record Update(
            @NotBlank(message = "El nombre de la talla es obligatorio")
            @jakarta.validation.constraints.Size(max = 30, message = "El nombre no puede exceder 30 caracteres")
            String name,

            Integer sortOrder
    ) {}

    public record Status(boolean enabled) {}

    public record Response(
            UUID id,
            String name,
            int sortOrder,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        public static Response from(Size s) {
            return new Response(
                    s.getId(),
                    s.getName(),
                    s.getSortOrder(),
                    s.isEnabled(),
                    s.getCreatedAt(),
                    s.getUpdatedAt(),
                    s.getVersion()
            );
        }
    }
}
