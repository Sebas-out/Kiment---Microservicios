package com.kiments.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public static ApiException unauthorized() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales o sesión inválidas");
  }

  public static ApiException forbidden() {
    return new ApiException(HttpStatus.FORBIDDEN, "Operación no autorizada");
  }

  public static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
  }

  public static ApiException conflict(String detail) {
    return new ApiException(HttpStatus.CONFLICT, detail);
  }
}
