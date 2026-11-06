package com.kiments.auth.security;

import com.kiments.auth.exception.ApiException;
import com.kiments.auth.sessions.RefreshTokens;
import com.kiments.auth.shared.EmailNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class LoginRateLimiter {
  private final JdbcTemplate jdbc;

  public LoginRateLimiter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
  public void check(String email, String ip) {
    int byIp = increment("ip:" + RefreshTokens.hash(ip));
    int byAccount = increment("account:" + RefreshTokens.hash(EmailNormalizer.normalize(email)));
    if (byIp > 50 || byAccount > 10)
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos; intente después de 15 minutos");
  }

  private int increment(String bucket) {
    return jdbc.queryForObject(
        """
        INSERT INTO auth.login_attempts(bucket,attempts,expires_at) VALUES (?,1,now()+interval '15 minutes')
        ON CONFLICT(bucket) DO UPDATE SET
          attempts=CASE WHEN auth.login_attempts.expires_at<=now() THEN 1 ELSE auth.login_attempts.attempts+1 END,
          expires_at=CASE WHEN auth.login_attempts.expires_at<=now() THEN now()+interval '15 minutes' ELSE auth.login_attempts.expires_at END
        RETURNING attempts
        """,
        Integer.class,
        bucket);
  }

  @Scheduled(fixedDelay = 900000)
  public void cleanup() {
    jdbc.update("DELETE FROM auth.login_attempts WHERE expires_at < now()");
  }
}
