package com.kiments.auth.permissions;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "permissions", schema = "auth")
@Getter
public class Permission {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false)
  private String description;

  protected Permission() {}
}
