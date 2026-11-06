package com.kiments.auth;

import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class AuthIntegrationScenariosKeys {
  @DynamicPropertySource
  static void keys(DynamicPropertyRegistry r) throws Exception {
    var g = KeyPairGenerator.getInstance("RSA");
    g.initialize(2048);
    var pair = g.generateKeyPair();
    var dir = Path.of("target", "stage2-test-keys");
    Files.createDirectories(dir);
    var pub = dir.resolve("public.pem");
    var priv = dir.resolve("private.pem");
    Files.writeString(pub, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    Files.writeString(priv, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    r.add("app.jwt.public-key", () -> pub.toUri().toString());
    r.add("app.jwt.private-key", () -> priv.toUri().toString());
    r.add("app.jwt.issuer", () -> "https://issuer.kiments.test");
    r.add("app.jwt.audience", () -> "kiments-api");
  }

  private static String pem(String type, byte[] bytes) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(bytes)
        + "\n-----END "
        + type
        + "-----\n";
  }
}
