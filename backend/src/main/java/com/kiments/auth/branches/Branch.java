package com.kiments.auth.branches;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "branches", schema = "auth")
@Getter
public class Branch {

  @Id private UUID id = UUID.randomUUID();

  /**
   * Código corto único de la sucursal. Solo mayúsculas, dígitos, guion y guion bajo.
   * Inmutable tras la creación para evitar referencias rotas en otros servicios.
   */
  @Column(nullable = false, unique = true, length = 20)
  private String code;

  @Setter
  @Column(nullable = false, length = 100)
  private String name;

  @Setter
  @Column(length = 200)
  private String address;

  @Setter
  @Column(length = 20)
  private String phone;

  @Setter
  @Column(length = 100)
  private String city;

  @Setter
  @Column(length = 6)
  private String ubigeo;

  @Setter
  @Column(length = 150)
  private String email;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "branch_type", length = 15)
  private BranchType branchType;

  @Setter
  @Column(name = "sunat_code", length = 4)
  private String sunatCode;

  @Setter
  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  /** Control de concurrencia optimista entre instancias del servicio. */
  @Version private long version;

  protected Branch() {}

  /**
   * Crea una sucursal nueva con código normalizado a mayúsculas. El código no puede modificarse
   * después de la creación.
   */
  public Branch(String code) {
    this.code = code.toUpperCase();
  }

  @PrePersist
  void create() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void update() {
    updatedAt = Instant.now();
  }
}
