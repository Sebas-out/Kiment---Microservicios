package com.kiments.auth.config.bootstrap;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InitializationRepository {
  private final JdbcTemplate jdbc;

  public InitializationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean isPending() {
    return "PENDING"
        .equals(
            jdbc.queryForObject(
                "SELECT status FROM auth.admin_initialization WHERE id=1", String.class));
  }

  public void blockExistingData() {
    jdbc.update(
        "UPDATE auth.admin_initialization SET"
            + " status='BLOCKED',recorded_at=now(),source='EXISTING_DATA' WHERE id=1 AND"
            + " status='PENDING'");
  }

  public void complete(UUID userId, String source) {
    int count =
        jdbc.update(
            "UPDATE auth.admin_initialization SET"
                + " status='INITIALIZED',recorded_at=now(),initial_user_id=?,source=? WHERE id=1"
                + " AND status='PENDING'",
            userId,
            source);
    if (count != 1)
      throw new IllegalStateException(
          "No se pudo registrar la inicialización; operación revertida");
  }

  public boolean hasSessions() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM auth.refresh_sessions)", Boolean.class));
  }
}
