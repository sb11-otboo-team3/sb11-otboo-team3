package com.otboo.domain.auth.token;

import com.otboo.domain.auth.jwt.JwtProperties;
import com.otboo.domain.user.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final String KEY_PREFIX = "refresh:";
  private static final String CONSUMED_KEY_PREFIX = "refresh:consumed:";
  private static final String DELIMITER = ":";

  private final StringRedisTemplate redisTemplate;
  private final JwtProperties jwtProperties;
  private final UserRepository userRepository;

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

  @Transactional
  public Optional<TokenInfo> consumeTokenInfo(String refreshToken) {
    String consumedValue = redisTemplate.opsForValue().get(CONSUMED_KEY_PREFIX + refreshToken);
    if (consumedValue != null) {
      handleReuseDetected(consumedValue, refreshToken);
      return Optional.empty();
    }

    String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + refreshToken);
    if (value == null) {
      return Optional.empty();
    }

    redisTemplate.opsForValue().set(
        CONSUMED_KEY_PREFIX + refreshToken,
        value,
        Duration.ofMillis(jwtProperties.refreshExpiration())
    );

    return parse(value);
  }

  private void handleReuseDetected(String consumedValue, String refreshToken) {
    parse(consumedValue).ifPresent(tokenInfo -> {
      log.warn(
          "이미 소비된 Refresh Token의 재사용이 감지되었습니다. 토큰 탈취가 의심되어 "
              + "해당 계정의 모든 세션을 무효화합니다. userId={}",
          tokenInfo.userId()
      );
      userRepository.incrementTokenVersion(tokenInfo.userId());
    });
  }

  private Optional<TokenInfo> parse(String value) {
    String[] parts = value.split(DELIMITER);
    if (parts.length != 2) {
      return Optional.empty();
    }
    try {
      UUID userId = UUID.fromString(parts[0]);
      long tokenVersion = Long.parseLong(parts[1]);
      return Optional.of(new TokenInfo(userId, tokenVersion));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public record TokenInfo(UUID userId, long tokenVersion) {
  }
}