package com.kiments.cataloginventory.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndActorUserIdAndEndpoint(
            String idempotencyKey, UUID actorUserId, String endpoint);
}
