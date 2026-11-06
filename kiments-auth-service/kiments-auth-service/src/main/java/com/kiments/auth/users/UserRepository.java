package com.kiments.auth.users;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
  @EntityGraph(attributePaths = {"role", "role.permissions"})
  Optional<User> findByEmail(String email);

  @Override
  @EntityGraph(attributePaths = {"role", "role.permissions"})
  Optional<User> findById(UUID id);

  boolean existsByEmail(String email);

  boolean existsByDni(String dni);

  long countByRoleCodeAndEnabledTrue(String code);
}
