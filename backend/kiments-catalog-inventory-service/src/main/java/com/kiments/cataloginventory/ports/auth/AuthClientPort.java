package com.kiments.cataloginventory.ports.auth;

import java.util.List;
import java.util.UUID;

public interface AuthClientPort {
    record AuthUserProfile(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String role,
            boolean enabled,
            UUID branchId,
            List<String> permissions
    ) {}

    AuthUserProfile validateSession(String bearerToken);
}
