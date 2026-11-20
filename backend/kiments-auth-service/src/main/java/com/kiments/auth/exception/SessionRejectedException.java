package com.kiments.auth.exception;

import org.springframework.http.HttpStatus;

/** Permite confirmar la revocación por reutilización antes de devolver 401. */
public class SessionRejectedException extends ApiException {
  public SessionRejectedException() {
    super(HttpStatus.UNAUTHORIZED, "Credenciales o sesión inválidas");
  }
}
