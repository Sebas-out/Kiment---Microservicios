package com.kiments.auth.sessions;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "refresh_sessions", schema = "auth")
@Getter
public class RefreshSession {
  @Id private UUID id = UUID.randomUUID();

  @Setter
  @Column(nullable = false)
  private UUID userId;

  @Setter
  @Column(nullable = false)
  private UUID familyId;

  @Setter
  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Setter
  @Column(nullable = false)
  private Instant createdAt;

  @Setter
  @Column(nullable = false)
  private Instant expiresAt;

  @Setter private Instant revokedAt;
  @Setter private UUID replacedBy;

  public RefreshSession() {}
}
