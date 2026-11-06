package com.kiments.auth.branches;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BranchDtos {
  private BranchDtos() {}

  public record Create(
      @NotBlank
          @Pattern(
              regexp = "[A-Za-z0-9_-]{2,20}",
              message = "El código debe tener entre 2 y 20 caracteres: mayúsculas, dígitos, - o _")
          String code,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 200) String address,
      @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,20}$") String phone,
      @Size(max = 100) String city,
      @Pattern(
              regexp = "^$|^[0-9]{6}$",
              message = "El ubigeo debe contener exactamente 6 dígitos numéricos (Departamento, Provincia y Distrito según INEI)")
          String ubigeo,
      @Pattern(
              regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
              message = "El formato de correo electrónico es inválido")
          @Size(max = 150)
          String email,
      @NotNull(message = "El tipo de sucursal es obligatorio (VENTA, ALMACEN o MIXTO)")
          BranchType branchType,
      @Pattern(
              regexp = "^$|^[0-9]{4}$",
              message = "El código de establecimiento SUNAT debe tener exactamente 4 dígitos numéricos")
          String sunatCode,
      @NotNull Boolean enabled) {

    /** Constructor de compatibilidad para creación rápida sin campos SUNAT opcionales. */
    public Create(
        String code,
        String name,
        String address,
        String phone,
        BranchType branchType,
        Boolean enabled) {
      this(code, name, address, phone, null, null, null, branchType, null, enabled);
    }
  }

  public record Update(
      @Size(min = 1, max = 100) @Pattern(regexp = "(?s).*\\S.*") String name,
      @Size(max = 200) String address,
      @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,20}$") String phone,
      @Size(max = 100) String city,
      @Pattern(
              regexp = "^$|^[0-9]{6}$",
              message = "El ubigeo debe contener exactamente 6 dígitos numéricos (Departamento, Provincia y Distrito según INEI)")
          String ubigeo,
      @Pattern(
              regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
              message = "El formato de correo electrónico es inválido")
          @Size(max = 150)
          String email,
      BranchType branchType,
      @Pattern(
              regexp = "^$|^[0-9]{4}$",
              message = "El código de establecimiento SUNAT debe tener exactamente 4 dígitos numéricos")
          String sunatCode) {}

  public record ChangeStatus(@NotNull Boolean enabled) {}

  public record Response(
      UUID id,
      String code,
      String name,
      String address,
      String phone,
      String city,
      String ubigeo,
      String email,
      BranchType branchType,
      String sunatCode,
      boolean enabled,
      Instant createdAt,
      Instant updatedAt) {}

  public record PageResponse<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {}
}
