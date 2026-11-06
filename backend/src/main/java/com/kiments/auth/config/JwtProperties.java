package com.kiments.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("app.jwt")
public record JwtProperties(
    String issuer,
    String audience,
    Resource publicKey,
    Resource privateKey,
    String keyId,
    Duration accessTtl) {}
