package com.kiments.auth.sessions;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** SHA-256 se utiliza solo para tokens aleatorios de alta entropía, nunca para contraseñas. */
public final class RefreshTokens {
  private static final SecureRandom RANDOM = new SecureRandom();

  private RefreshTokens() {}

  public static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
