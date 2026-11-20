package com.kiments.auth.users;

import com.kiments.auth.roles.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users", schema = "auth")
@Getter
public class User {
  @Id private UUID id = UUID.randomUUID();

  @Setter
  @Column(nullable = false, length = 80)
  private String firstName;

  @Setter
  @Column(nullable = false, length = 80)
  private String lastName;

  @Setter
  @Column(nullable = false, unique = true, length = 8)
  private String dni;

  @Setter
  @Column(length = 20)
  private String phone;

  @Setter
  @Column(nullable = false, length = 254)
  private String email;

  @Setter
  @Column(nullable = false, length = 255)
  private String passwordHash;

  @Setter
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id")
  private Role role;

  @Setter private UUID branchId;

  @Setter
  @Column(length = 255)
  private String profileImageKey;

  @Setter
  @Column(name = "active_family_id")
  private UUID activeFamilyId;

  @Setter
  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Setter private Instant lastLoginAt;
  @Version private long version;

  public User() {}

  @PrePersist
  void create() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
