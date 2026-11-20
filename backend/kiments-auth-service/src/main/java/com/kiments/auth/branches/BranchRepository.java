package com.kiments.auth.branches;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface BranchRepository
    extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {

  boolean existsByCodeIgnoreCase(String code);

  Optional<Branch> findByCodeIgnoreCase(String code);

  boolean existsBySunatCode(String sunatCode);

  boolean existsBySunatCodeAndIdNot(String sunatCode, UUID id);

  Optional<Branch> findBySunatCode(String sunatCode);

  /**
   * Cuenta trabajadores con roles operativos (no ADMIN) habilitados asignados a esta sucursal. Se
   * utiliza para proteger la desactivación de una sucursal con personal activo.
   */
  @Query(
      """
      SELECT COUNT(u) FROM User u
      WHERE u.branchId = :branchId
        AND u.enabled = true
        AND u.role.code <> 'ADMIN'
      """)
  long countActiveWorkers(UUID branchId);
}
