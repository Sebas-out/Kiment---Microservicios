package com.kiments.cataloginventory.security;

import com.kiments.cataloginventory.config.AuthClientProperties;
import com.kiments.cataloginventory.exception.ApiException;
import com.kiments.cataloginventory.ports.auth.AuthClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SecurityAndTokenTests {

    private AuthClientProperties properties;
    private AuthClientPort authClient;
    private CatalogJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        properties = new AuthClientProperties();
        properties.setAudience("kiments-auth,kiments-api");
        properties.setIssuer("https://kiments-auth-service,http://localhost:8080");
        properties.setValidateSessionRemotely(false);
        authClient = Mockito.mock(AuthClientPort.class);
        converter = new CatalogJwtAuthenticationConverter(properties, authClient);
    }

    @Test
    @DisplayName("Debe autenticar correctamente a un administrador global sin branch_id")
    void adminUserAuthenticatedSuccessfully() {
        UUID adminId = UUID.randomUUID();
        Jwt jwt = TestJwtUtils.toSpringJwt(
                adminId,
                "ADMIN",
                List.of("PRODUCT_READ", "PRODUCT_WRITE", "CATEGORY_WRITE", "STOCK_READ", "STOCK_ADJUST"),
                null,
                UUID.randomUUID(),
                "kiments-auth"
        );

        AbstractAuthenticationToken auth = converter.convert(jwt);
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(CurrentUser.class);
        CurrentUser user = (CurrentUser) auth.getPrincipal();
        assertThat(user.userId()).isEqualTo(adminId);
        assertThat(user.isAdmin()).isTrue();
        assertThat(user.branchId()).isNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("PRODUCT_READ"));
    }

    @Test
    @DisplayName("Debe autenticar a un usuario operativo asignado a sucursal")
    void operativeUserAuthenticatedWithBranch() {
        UUID userId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Jwt jwt = TestJwtUtils.toSpringJwt(
                userId,
                "ALMACEN",
                List.of("PRODUCT_READ", "STOCK_READ", "STOCK_ADJUST"),
                branchId,
                UUID.randomUUID(),
                "kiments-auth"
        );

        AbstractAuthenticationToken auth = converter.convert(jwt);
        assertThat(auth).isNotNull();
        CurrentUser user = (CurrentUser) auth.getPrincipal();
        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.branchId()).isEqualTo(branchId);
        assertThat(user.canAccessBranch(branchId)).isTrue();
        assertThat(user.canAccessBranch(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar token cuando la audiencia no coincide")
    void rejectsTokenWithWrongAudience() {
        Jwt jwt = TestJwtUtils.toSpringJwt(
                UUID.randomUUID(),
                "ADMIN",
                List.of("PRODUCT_READ"),
                null,
                UUID.randomUUID(),
                "untrusted-audience"
        );

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Audience no válida");
    }

    @Test
    @DisplayName("Debe rechazar token operativo que carece de branch_id")
    void rejectsOperativeUserWithoutBranchId() {
        Jwt jwt = TestJwtUtils.toSpringJwt(
                UUID.randomUUID(),
                "ALMACEN",
                List.of("PRODUCT_READ"),
                null, // Missing branch_id
                UUID.randomUUID(),
                "kiments-auth"
        );

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("branch_id requerido");
    }

    @Test
    @DisplayName("Debe rechazar token ADMIN que incluye branch_id específico")
    void rejectsAdminWithBranchId() {
        Jwt jwt = TestJwtUtils.toSpringJwt(
                UUID.randomUUID(),
                "ADMIN",
                List.of("PRODUCT_READ"),
                UUID.randomUUID(), // ADMIN cannot have branch_id
                UUID.randomUUID(),
                "kiments-auth"
        );

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("ADMIN no debe tener branch_id");
    }

    @Test
    @DisplayName("Debe rechazar autenticación cuando la validación remota con Auth reporta usuario desactivado")
    void rejectsWhenRemoteSessionDisabled() {
        properties.setValidateSessionRemotely(true);

        UUID userId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Jwt jwt = TestJwtUtils.toSpringJwt(
                userId,
                "VENTAS",
                List.of("PRODUCT_READ"),
                branchId,
                UUID.randomUUID(),
                "kiments-auth"
        );

        when(authClient.validateSession(anyString())).thenReturn(
                new AuthClientPort.AuthUserProfile(userId, "Test", "User", "test@kiments.com", "VENTAS", false, branchId, List.of("PRODUCT_READ"))
        );

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Usuario desactivado");
    }

    @Test
    @DisplayName("Debe lanzar ApiException 503 cuando Auth no está disponible")
    void throws503WhenAuthUnavailable() {
        properties.setValidateSessionRemotely(true);

        UUID userId = UUID.randomUUID();
        Jwt jwt = TestJwtUtils.toSpringJwt(
                userId,
                "ADMIN",
                List.of("PRODUCT_READ"),
                null,
                UUID.randomUUID(),
                "kiments-auth"
        );

        when(authClient.validateSession(anyString())).thenThrow(
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE", "Servicio no disponible")
        );

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }
}
