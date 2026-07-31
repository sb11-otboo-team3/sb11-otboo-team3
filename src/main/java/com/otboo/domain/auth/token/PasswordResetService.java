package com.otboo.domain.auth.token;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetService {

  private static final String KEY_PREFIX = "temp-password:";
  private static final String TEMPORARY_PASSWORD = "temporary1!!";
  private static final Duration TTL = Duration.ofMinutes(3);

  private final StringRedisTemplate redisTemplate;

  public void issue(UUID userId) {
    redisTemplate.opsForValue().set(KEY_PREFIX + userId, TEMPORARY_PASSWORD, TTL);
  }

  public Optional<String> find(UUID userId) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
  }

  public void delete(UUID userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }
}