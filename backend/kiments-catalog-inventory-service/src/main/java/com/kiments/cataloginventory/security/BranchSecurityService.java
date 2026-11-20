package com.kiments.cataloginventory.security;

import com.kiments.cataloginventory.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("branchSecurity")
public class BranchSecurityService {

    public CurrentUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sesión de usuario no encontrada o no autenticada");
    }

    public boolean canAccessBranch(UUID targetBranchId) {
        if (targetBranchId == null) {
            return false;
        }
        CurrentUser user = getCurrentUser();
        return user.canAccessBranch(targetBranchId);
    }

    public void enforceBranchAccess(UUID targetBranchId) {
        if (!canAccessBranch(targetBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED",
                    "No tiene autorización para operar o consultar la sucursal especificada");
        }
    }

    public UUID resolveBranchScope(UUID requestedBranchId) {
        CurrentUser user = getCurrentUser();
        if (user.isAdmin()) {
            return requestedBranchId; // ADMIN can choose any branch or null for all
        }
        // Non-admin can ONLY query/operate their own assigned branch
        if (requestedBranchId != null && !requestedBranchId.equals(user.branchId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BRANCH_ACCESS_DENIED",
                    "No tiene permisos para acceder a una sucursal distinta a la suya asignada");
        }
        return user.branchId();
    }
}
