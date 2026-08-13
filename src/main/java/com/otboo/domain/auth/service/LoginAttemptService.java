package com.otboo.domain.auth.service;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAttemptService {

  private static final String ATTEMPT_KEY_PREFIX = "login:attempt:";
  private static final String BLOCK_KEY_PREFIX = "login:block:";
  private static final int MAX_ATTEMPTS = 5;
  private static final long ATTEMPT_WINDOW_SECONDS = Duration.ofMinutes(5).getSeconds();
  private static final long BLOCK_DURATION_SECONDS = Duration.ofMinutes(15).getSeconds();

  // 증가 -> (최초 실패 시) TTL 설정 -> (임계치 도달 시) 차단 설정 + 카운터 삭제를
  // 하나의 원자적 연산으로 묶는다. 중간에 애플리케이션이 죽어도 일관성이 깨지지 않는다.
  private static final String RECORD_FAILURE_SCRIPT = """
      local attemptKey = KEYS[1]
      local blockKey = KEYS[2]
      local maxAttempts = tonumber(ARGV[1])
      local attemptWindowSeconds = tonumber(ARGV[2])
      local blockDurationSeconds = tonumber(ARGV[3])

      local attempts = redis.call('INCR', attemptKey)
      if attempts == 1 then
        redis.call('EXPIRE', attemptKey, attemptWindowSeconds)
      end

      if attempts >= maxAttempts then
        redis.call('SET', blockKey, 'blocked', 'EX', blockDurationSeconds)
        redis.call('DEL', attemptKey)
      end

      return attempts
      """;

  private final StringRedisTemplate redisTemplate;

  private final DefaultRedisScript<Long> recordFailureScript = buildScript();

  private DefaultRedisScript<Long> buildScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(RECORD_FAILURE_SCRIPT);
    script.setResultType(Long.class);
    return script;
  }

  public boolean isBlocked(String email) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCK_KEY_PREFIX + email));
  }

  // 로그인 실패 시 호출. 실패 횟수가 임계치를 넘으면 계정을 일정 시간 차단한다.
  // 증가/TTL설정/차단설정/카운터삭제를 Lua script로 원자적으로 처리한다.
  public void recordFailure(String email) {
    redisTemplate.execute(
        recordFailureScript,
        List.of(ATTEMPT_KEY_PREFIX + email, BLOCK_KEY_PREFIX + email),
        String.valueOf(MAX_ATTEMPTS),
        String.valueOf(ATTEMPT_WINDOW_SECONDS),
        String.valueOf(BLOCK_DURATION_SECONDS)
    );
  }

  // 로그인 성공 시 호출. 실패 카운트를 초기화한다.
  public void recordSuccess(String email) {
    redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
  }
}