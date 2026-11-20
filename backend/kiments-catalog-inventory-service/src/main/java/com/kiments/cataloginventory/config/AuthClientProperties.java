package com.kiments.cataloginventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthClientProperties {
    private String audience = "kiments-api,kiments-auth";
    private String issuer = "http://localhost:8080,https://kiments-auth-service";
    private String meUri = "http://localhost:8080/api/v1/auth/me";
    private boolean validateSessionRemotely = false;
    private Duration connectTimeout = Duration.ofMillis(3000);
    private Duration readTimeout = Duration.ofMillis(5000);

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getMeUri() {
        return meUri;
    }

    public void setMeUri(String meUri) {
        this.meUri = meUri;
    }

    public boolean isValidateSessionRemotely() {
        return validateSessionRemotely;
    }

    public void setValidateSessionRemotely(boolean validateSessionRemotely) {
        this.validateSessionRemotely = validateSessionRemotely;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
