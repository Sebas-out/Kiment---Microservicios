package com.kiments.cataloginventory.security;

import com.kiments.cataloginventory.config.AuthClientProperties;
import com.kiments.cataloginventory.ports.auth.AuthClientPort;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CatalogJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthClientProperties authProperties;
    private final AuthClientPort authClient;

    public CatalogJwtAuthenticationConverter(AuthClientProperties authProperties, AuthClientPort authClient) {
        this.authProperties = authProperties;
        this.authClient = authClient;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Validar Audience
        List<String> audiences = jwt.getAudience();
        List<String> allowedAudiences = Arrays.stream(authProperties.getAudience().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (audiences == null || audiences.stream().noneMatch(allowedAudiences::contains)) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Audience no válida para este servicio", null));
        }

        // Validar Issuer
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
        List<String> allowedIssuers = Arrays.stream(authProperties.getIssuer().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (!issuer.isBlank() && !allowedIssuers.isEmpty() && !allowedIssuers.contains(issuer)) {
            if (allowedIssuers.stream().noneMatch(a -> issuer.replace("https://", "http://").equals(a.replace("https://", "http://")))) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Issuer no válido para este servicio", null));
            }
        }

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Subject vacío", null));
        }
        UUID userId;
        try {
            userId = UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Subject no es un UUID válido", null));
        }

        String role = jwt.getClaimAsString("role");
        if (role == null || role.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Claim role ausente", null));
        }

        List<String> rawPermissions = jwt.getClaimAsStringList("permissions");
        Set<String> permissions = new HashSet<>(rawPermissions != null ? rawPermissions : Collections.emptyList());

        String branchIdStr = jwt.getClaimAsString("branch_id");
        UUID branchId = null;
        if (branchIdStr != null && !branchIdStr.isBlank()) {
            try {
                branchId = UUID.fromString(branchIdStr);
            } catch (IllegalArgumentException e) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "branch_id no es un UUID", null));
            }
        }

        // Si es rol operativo, el branch_id es obligatorio en el token
        if (!"ADMIN".equalsIgnoreCase(role) && branchId == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "branch_id requerido para usuarios operativos", null));
        }
        // Si es ADMIN, no debe estar restringido a una sucursal
        if ("ADMIN".equalsIgnoreCase(role) && branchId != null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "ADMIN no debe tener branch_id específico", null));
        }

        String familyIdStr = jwt.getClaimAsString("family_id");
        UUID familyId = null;
        if (familyIdStr != null && !familyIdStr.isBlank()) {
            try {
                familyId = UUID.fromString(familyIdStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Validación remota opcional de sesión activa con Auth (GET /api/v1/auth/me)
        if (authProperties.isValidateSessionRemotely()) {
            AuthClientPort.AuthUserProfile remoteProfile = authClient.validateSession(jwt.getTokenValue());
            if (remoteProfile != null) {
                if (!remoteProfile.enabled()) {
                    throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Usuario desactivado", null));
                }
                role = remoteProfile.role();
                permissions.addAll(remoteProfile.permissions());
                branchId = remoteProfile.branchId();
            }
        }

        CurrentUser currentUser = new CurrentUser(userId, role, permissions, branchId, familyId);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String perm : permissions) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }

        return new UsernamePasswordAuthenticationToken(currentUser, jwt, authorities);
    }
}
