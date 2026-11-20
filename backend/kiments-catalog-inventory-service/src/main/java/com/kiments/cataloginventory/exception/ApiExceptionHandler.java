package com.kiments.cataloginventory.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException e, HttpServletRequest request) {
        log.warn("Excepción de negocio [{}]: {}", e.getCode(), e.getMessage());
        ProblemDetail problem = buildProblem(e.getStatus(), e.getMessage(), request);
        problem.setProperty("code", e.getCode());
        return ResponseEntity.status(e.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Revise los campos de la solicitud", request);
        problem.setProperty("code", "VALIDATION_ERROR");
        var errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        problem.setProperty("fieldErrors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception e, HttpServletRequest request) {
        log.debug("Solicitud inválida: {}", e.getMessage());
        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Solicitud inválida o parámetros incorrectos", request);
        problem.setProperty("code", "INVALID_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class
    })
    public ResponseEntity<ProblemDetail> handleConflict(Exception e, HttpServletRequest request) {
        log.warn("Conflicto de integridad o bloqueo optimista: {}", e.getMessage());
        ProblemDetail problem = buildProblem(HttpStatus.CONFLICT,
                "Conflicto de datos: valor duplicado o actualización concurrente. Consulte el recurso e intente nuevamente.", request);
        problem.setProperty("code", "RESOURCE_CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(AccessDeniedException e, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(HttpStatus.FORBIDDEN, "Operación no autorizada o fuera del alcance de sucursal asignada", request);
        problem.setProperty("code", "FORBIDDEN");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo excede el tamaño máximo permitido", request);
        problem.setProperty("code", "PAYLOAD_TOO_LARGE");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMedia(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido (Content-Type) no admitido", request);
        problem.setProperty("code", "UNSUPPORTED_MEDIA_TYPE");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(HttpStatus.NOT_FOUND, "Recurso no encontrado", request);
        problem.setProperty("code", "RESOURCE_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Error inesperado en el servidor", e);
        if (e instanceof ErrorResponse error) {
            ProblemDetail problem = buildProblem(HttpStatus.valueOf(error.getStatusCode().value()), "Solicitud HTTP no admitida", request);
            problem.setProperty("code", "HTTP_ERROR");
            return new ResponseEntity<>(problem, error.getHeaders(), error.getStatusCode());
        }
        ProblemDetail problem = buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación solicitada", request);
        problem.setProperty("code", "INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private ProblemDetail buildProblem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
