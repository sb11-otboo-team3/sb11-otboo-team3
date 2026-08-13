package com.otboo.domain.auth.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAttemptService {

  private static final String ATTEMPT_KEY_PREFIX = "login:attempt:";
  private static final String BLOCK_KEY_PREFIX = "login:block:";
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(5);
  private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

  private final StringRedisTemplate redisTemplate;

  public boolean isBlocked(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCK_KEY_PREFIX + email));
  }

  // 로그인 실패 시 호출. 실패 횟수가 임계치를 넘으면 계정을 일정 시간 차단한다.
  public void recordFailure(String email) {
    String attemptKey = ATTEMPT_KEY_PREFIX + email;

    Long attempts = redisTemplate.opsForValue().increment(attemptKey);
    if (attempts != null && attempts == 1L) {
      // 첫 실패 시에만 만료 시간을 새로 건다 (윈도우 시작점 고정).
      redisTemplate.expire(attemptKey, ATTEMPT_WINDOW);
    }

    if (attempts != null && attempts >= MAX_ATTEMPTS) {
      redisTemplate.opsForValue().set(
          BLOCK_KEY_PREFIX + email, "blocked", BLOCK_DURATION
      );
      redisTemplate.delete(attemptKey);
    }
  }

  // 로그인 성공 시 호출. 실패 카운트를 초기화한다.
  public void recordSuccess(String email) {
    redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
  }
}