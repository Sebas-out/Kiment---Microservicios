package com.kiments.auth.roles;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface RoleRepository extends JpaRepository<Role, Long> {
  @EntityGraph(attributePaths = "permissions")
  Optional<Role> findByCode(String code);

  @Override
  @EntityGraph(attributePaths = "permissions")
  List<Role> findAll();

  @Override
  @EntityGraph(attributePaths = "permissions")
  Optional<Role> findById(Long id);
}
