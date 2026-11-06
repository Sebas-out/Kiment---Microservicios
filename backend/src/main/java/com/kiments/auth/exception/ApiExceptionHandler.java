package com.kiments.auth.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ProblemDetail business(ApiException e) {
    return problem(e.getStatus(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException e) {
    var result = problem(HttpStatus.BAD_REQUEST, "Revise los campos de la solicitud");
    result.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
            .toList());
    return result;
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ProblemDetail badRequest(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida");
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ProblemDetail conflict(Exception e) {
    return problem(
        HttpStatus.CONFLICT,
        "Datos duplicados o actualización concurrente; consulte el recurso e intente nuevamente");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail forbidden(Exception e) {
    return problem(HttpStatus.FORBIDDEN, "Operación no autorizada");
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ProblemDetail maxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
    return problem(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "El archivo excede el tamaño máximo permitido de 2 MB");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail missing(Exception e) {
    return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    // Conservar los estados y cabeceras HTTP definidos por Spring (Allow, Accept, etc.)
    // sin publicar sus detalles internos ni los valores de la solicitud.
    if (e instanceof ErrorResponse error) {
      var detail =
          ProblemDetail.forStatusAndDetail(error.getStatusCode(), "Solicitud HTTP no admitida");
      return new ResponseEntity<>(detail, error.getHeaders(), error.getStatusCode());
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación"));
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    var result = ProblemDetail.forStatusAndDetail(status, detail);
    result.setTitle(status.getReasonPhrase());
    return result;
  }
}
