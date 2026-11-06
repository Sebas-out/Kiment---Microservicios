package com.kiments.auth.sessions;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
  Optional<RefreshSession> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update RefreshSession s set s.revokedAt=:now where s.userId=:userId and s.revokedAt is null")
  void revokeUser(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying
  @Query(
      "update RefreshSession s set s.revokedAt=:now where s.familyId=:familyId and s.revokedAt is"
          + " null")
  void revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
