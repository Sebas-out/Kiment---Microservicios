package com.kiments.auth.config;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class TokenConfiguration {
  @Bean
  RSAKey rsaKey(JwtProperties p) throws Exception {
    RSAPublicKey publicKey;
    RSAPrivateKey privateKey;
    try (var input = p.publicKey().getInputStream()) {
      publicKey = RsaKeyConverters.x509().convert(input);
    }
    try (var input = p.privateKey().getInputStream()) {
      privateKey = RsaKeyConverters.pkcs8().convert(input);
    }
    if (publicKey == null
        || privateKey == null
        || publicKey.getModulus().bitLength() < 2048
        || !publicKey.getModulus().equals(privateKey.getModulus()))
      throw new IllegalStateException("Se requiere un par RSA válido de al menos 2048 bits");
    if (p.issuer() == null
        || p.issuer().isBlank()
        || p.audience() == null
        || p.audience().isBlank()
        || p.accessTtl() == null
        || p.accessTtl().isNegative()
        || p.accessTtl().isZero()) throw new IllegalStateException("Configuración JWT inválida");
    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(p.keyId())
        .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
        .build();
  }

  @Bean
  JwtEncoder jwtEncoder(RSAKey key) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  }

  @Bean
  JwtDecoder jwtDecoder(RSAKey key, JwtProperties p) throws Exception {
    var decoder =
        NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    var time = new JwtTimestampValidator(Duration.ZERO);
    OAuth2TokenValidator<Jwt> required =
        jwt -> {
          boolean valid =
              jwt.getAudience().contains(p.audience())
                  && jwt.getExpiresAt() != null
                  && jwt.getNotBefore() != null
                  && jwt.getIssuedAt() != null
                  && jwt.getId() != null;
          try {
            UUID.fromString(jwt.getSubject());
            UUID.fromString(jwt.getId());
          } catch (Exception e) {
            valid = false;
          }
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(time, new JwtIssuerValidator(p.issuer()), required));
    return decoder;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    // Argon2id: sal de 16 bytes, hash de 32 bytes, memoria 19 MiB, 2 iteraciones.
    return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
  }
}
