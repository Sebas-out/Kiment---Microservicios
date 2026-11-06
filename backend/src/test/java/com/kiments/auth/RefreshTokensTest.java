package com.kiments.auth;

import static org.assertj.core.api.Assertions.*;

import com.kiments.auth.sessions.RefreshTokens;
import org.junit.jupiter.api.Test;

class RefreshTokensTest {
  @Test
  void tokensAreOpaqueDistinctAndHashed() {
    var token = RefreshTokens.generate();
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    assertThat(token).isNotEqualTo(RefreshTokens.generate());
    assertThat(RefreshTokens.hash(token))
        .matches("[0-9a-f]{64}")
        .isEqualTo(RefreshTokens.hash(token))
        .isNotEqualTo(token);
  }
}
