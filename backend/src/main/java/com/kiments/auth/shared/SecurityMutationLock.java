package com.kiments.auth.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/**
 * Serializa mutaciones sensibles entre todas las instancias del servicio. Debe adquirirse antes de
 * leer usuarios/sesiones dentro de la transacción.
 */
@Component
public class SecurityMutationLock {
  private final JdbcTemplate jdbc;

  public SecurityMutationLock(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void acquire() {
    jdbc.queryForObject("SELECT id FROM auth.security_lock WHERE id=1 FOR UPDATE", Integer.class);
  }
}
