package com.kiments.auth.security;

import com.kiments.auth.config.JwtProperties;
import com.kiments.auth.users.User;
import java.time.*;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final JwtProperties properties;

  public TokenService(JwtEncoder encoder, JwtProperties properties) {
    this.encoder = encoder;
    this.properties = properties;
  }

  public String issue(User user, UUID familyId) {
    Instant now = Instant.now();
    var claimsBuilder =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.getId().toString())
            .audience(java.util.List.of(properties.audience()))
            .issuedAt(now)
            .notBefore(now)
            .expiresAt(now.plus(properties.accessTtl()))
            .id(UUID.randomUUID().toString())
            .claim("role", user.getRole().getCode())
            .claim(
                "permissions",
                user.getRole().getPermissions().stream().map(p -> p.getCode()).sorted().toList());

    if (familyId != null) {
      claimsBuilder.claim("family_id", familyId.toString());
    }

    if (!user.getRole().getCode().equals("ADMIN") && user.getBranchId() != null) {
      claimsBuilder.claim("branch_id", user.getBranchId().toString());
    }

    var claims = claimsBuilder.build();
    var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public String issue(User user) {
    return issue(user, user.getActiveFamilyId());
  }

  public long expiresIn() {
    return properties.accessTtl().toSeconds();
  }
}
