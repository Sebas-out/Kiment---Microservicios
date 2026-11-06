package com.kiments.auth.roles;

import com.kiments.auth.permissions.Permission;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

@Entity
@Table(name = "roles", schema = "auth")
@Getter
public class Role {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 30)
  private String code;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false)
  private String description;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "role_permissions",
      schema = "auth",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<Permission> permissions = new HashSet<>();

  protected Role() {}
}
