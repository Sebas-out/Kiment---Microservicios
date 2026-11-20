package com.kiments.cataloginventory.catalog.dto;

import com.kiments.cataloginventory.catalog.domain.Color;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ColorDtos {
    private ColorDtos() {}

    public record Create(
            @NotBlank(message = "El nombre del color es obligatorio")
            @Size(max = 50, message = "El nombre no puede exceder 50 caracteres")
            String name,

            @NotBlank(message = "El código HEX es obligatorio")
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El código HEX debe tener formato #RRGGBB (ej. #FFFFFF)")
            String hex,

            Boolean enabled
    ) {}

    public record Update(
            @NotBlank(message = "El nombre del color es obligatorio")
            @Size(max = 50, message = "El nombre no puede exceder 50 caracteres")
            String name,

            @NotBlank(message = "El código HEX es obligatorio")
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El código HEX debe tener formato #RRGGBB (ej. #FFFFFF)")
            String hex
    ) {}

    public record Status(boolean enabled) {}

    public record Response(
            UUID id,
            String name,
            String hex,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        public static Response from(Color c) {
            return new Response(
                    c.getId(),
                    c.getName(),
                    c.getHex(),
                    c.isEnabled(),
                    c.getCreatedAt(),
                    c.getUpdatedAt(),
                    c.getVersion()
            );
        }
    }
}
