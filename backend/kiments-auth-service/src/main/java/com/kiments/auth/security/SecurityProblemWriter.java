package com.kiments.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemWriter {
  private final ObjectMapper json;

  public SecurityProblemWriter(ObjectMapper json) {
    this.json = json;
  }

  public void write(HttpServletResponse response, HttpStatus status) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    var problem =
        ProblemDetail.forStatusAndDetail(
            status,
            status == HttpStatus.UNAUTHORIZED
                ? "Autenticación requerida o token inválido"
                : "Operación no autorizada");
    problem.setTitle(status.getReasonPhrase());
    if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
    json.writeValue(response.getOutputStream(), problem);
  }
}
