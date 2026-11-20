package com.kiments.cataloginventory.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.*;

public class TestJwtUtils {

    public static final RSAKey RSA_KEY;
    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID("kiments-test-key").generate();
        } catch (JOSEException e) {
            throw new RuntimeException("Error al generar llaves RSA para pruebas", e);
        }
    }

    public static String createToken(
            UUID userId,
            String role,
            List<String> permissions,
            UUID branchId,
            UUID familyId,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt) {

        try {
            JWSSigner signer = new RSASSASigner(RSA_KEY);

            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(issuedAt))
                    .notBeforeTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("role", role)
                    .claim("permissions", permissions);

            if (familyId != null) {
                builder.claim("family_id", familyId.toString());
            }
            if (branchId != null) {
                builder.claim("branch_id", branchId.toString());
            }

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
                    builder.build()
            );

            signedJWT.sign(signer);
            return signedJWT.serialize();

        } catch (Exception e) {
            throw new RuntimeException("Error al firmar JWT de prueba", e);
        }
    }

    public static Jwt toSpringJwt(
            UUID userId,
            String role,
            List<String> permissions,
            UUID branchId,
            UUID familyId,
            String audience) {

        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId.toString());
        claims.put("role", role);
        claims.put("permissions", permissions);
        claims.put("aud", List.of(audience));
        claims.put("iss", "https://kiments-auth-service");
        if (branchId != null) {
            claims.put("branch_id", branchId.toString());
        }
        if (familyId != null) {
            claims.put("family_id", familyId.toString());
        }

        Map<String, Object> headers = Map.of("alg", "RS256", "kid", "test-key");

        return new Jwt("mock-token-value", now, now.plusSeconds(3600), headers, claims);
    }
}
