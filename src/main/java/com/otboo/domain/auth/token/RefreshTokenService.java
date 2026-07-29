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
  private static final String DELIMITER = ":";

  private final StringRedisTemplate redisTemplate;
  private final JwtProperties jwtProperties;

  public String issue(UUID userId, long tokenVersion) {
    String refreshToken = UUID.randomUUID().toString();
    String value = userId + DELIMITER + tokenVersion;
    redisTemplate.opsForValue().set(
        KEY_PREFIX + refreshToken,
        value,
        Duration.ofMillis(jwtProperties.refreshExpiration())
    );
    return refreshToken;
  }

  public Optional<TokenInfo> consumeTokenInfo(String refreshToken) {
    String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + refreshToken);
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(parse(value));
  }

  public boolean exists(String refreshToken) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + refreshToken));
  }

  public void delete(String refreshToken) {
    redisTemplate.delete(KEY_PREFIX + refreshToken);
  }

  private TokenInfo parse(String value) {
    String[] parts = value.split(DELIMITER);
    return new TokenInfo(UUID.fromString(parts[0]), Long.parseLong(parts[1]));
  }

  public record TokenInfo(UUID userId, long tokenVersion) {
  }
}