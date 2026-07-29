package com.otboo.domain.auth.token;

import com.otboo.domain.auth.jwt.JwtProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final String KEY_PREFIX = "refresh:";

  private final StringRedisTemplate redisTemplate;
  private final JwtProperties jwtProperties;

  public String issue(UUID userId) {
    String refreshToken = UUID.randomUUID().toString();
    redisTemplate.opsForValue().set(
        KEY_PREFIX + refreshToken,
        userId.toString(),
        Duration.ofMillis(jwtProperties.refreshExpiration())
    );
    return refreshToken;
  }

  public Optional<UUID> findUserId(String refreshToken) {
    String value = redisTemplate.opsForValue().get(KEY_PREFIX + refreshToken);
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(value));
  }

  public void delete(String refreshToken) {
    redisTemplate.delete(KEY_PREFIX + refreshToken);
  }

  public boolean exists(String refreshToken) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + refreshToken));
  }
}