package com.kiments.auth.config;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class TokenConfigurationTest {
  TokenConfiguration config = new TokenConfiguration();
  JwtProperties properties =
      new JwtProperties(
          "https://issuer.test", "kiments-api", null, null, "test-key", Duration.ofMinutes(15));
  RSAKey key;
  JwtDecoder decoder;

  @BeforeEach
  void setup() throws Exception {
    key = key();
    decoder = config.jwtDecoder(key, properties);
  }

  RSAKey key() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
        .privateKey((RSAPrivateKey) pair.getPrivate())
        .keyID("test-key")
        .build();
  }

  String token(String issuer, String audience, Instant nbf, Instant exp, RSAKey signingKey) {
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(UUID.randomUUID().toString())
            .audience(List.of(audience))
            .issuedAt(Instant.now().minusSeconds(600))
            .notBefore(nbf)
            .expiresAt(exp)
            .id(UUID.randomUUID().toString())
            .build();
    return config
        .jwtEncoder(signingKey)
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(), claims))
        .getTokenValue();
  }

  @Test
  void acceptsValidRsaToken() {
    assertThat(
            decoder
                .decode(
                    token(
                        properties.issuer(),
                        properties.audience(),
                        Instant.now().minusSeconds(10),
                        Instant.now().plusSeconds(900),
                        key))
                .getAudience())
        .contains("kiments-api");
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "issuer", "audience", "not-before", "signature"})
  void rejectsInvalidTokens(String condition) throws Exception {
    var issuer = condition.equals("issuer") ? "https://other.test" : properties.issuer();
    var audience = condition.equals("audience") ? "other-api" : properties.audience();
    var nbf =
        condition.equals("not-before")
            ? Instant.now().plusSeconds(60)
            : Instant.now().minusSeconds(100);
    var exp =
        condition.equals("expired")
            ? Instant.now().minusSeconds(2)
            : Instant.now().plusSeconds(900);
    var signer = condition.equals("signature") ? key() : key;
    String encoded = token(issuer, audience, nbf, exp, signer);
    assertThatThrownBy(() -> decoder.decode(encoded)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsHmacAlgorithmEvenWithAValidHmacSignature() throws Exception {
    byte[] secret = new byte[32];
    new SecureRandom().nextBytes(secret);
    var token =
        new com.nimbusds.jwt.SignedJWT(
            new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
            new com.nimbusds.jwt.JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .build());
    token.sign(new com.nimbusds.jose.crypto.MACSigner(secret));
    assertThatThrownBy(() -> decoder.decode(token.serialize())).isInstanceOf(JwtException.class);
  }
}
