package com.kiments.cataloginventory.security;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record CurrentUser(
        UUID userId,
        String role,
        Set<String> permissions,
        UUID branchId,
        UUID familyId
) {
    public CurrentUser {
        permissions = permissions != null ? Set.copyOf(permissions) : Collections.emptySet();
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean canAccessBranch(UUID targetBranchId) {
        if (isAdmin()) {
            return true;
        }
        return branchId != null && branchId.equals(targetBranchId);
    }
}
