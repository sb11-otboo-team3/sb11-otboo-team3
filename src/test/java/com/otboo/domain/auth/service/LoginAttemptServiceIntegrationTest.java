package com.otboo.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class LoginAttemptServiceIntegrationTest {

  private static final String ATTEMPT_KEY_PREFIX = "login:attempt:";
  private static final String BLOCK_KEY_PREFIX = "login:block:";

  @Autowired
  private LoginAttemptService loginAttemptService;

  @Autowired
  private StringRedisTemplate redisTemplate;

  private String email;

  @AfterEach
  void tearDown() {
    if (email != null) {
      redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
      redisTemplate.delete(BLOCK_KEY_PREFIX + email);
    }
  }

  @Test
  @DisplayName("5회 미만 실패하면 차단되지 않는다")
  void doesNotBlockBeforeReachingThreshold() {
    // given
    email = "attempt-" + UUID.randomUUID() + "@otboo.io";

    // when
    for (int i = 0; i < 4; i++) {
      loginAttemptService.recordFailure(email);
    }

    // then
    assertThat(loginAttemptService.isBlocked(email)).isFalse();
    String attempts = redisTemplate.opsForValue().get(ATTEMPT_KEY_PREFIX + email);
    assertThat(attempts).isEqualTo("4");
  }

  @Test
  @DisplayName("5회 실패하면 차단되고, 실패 카운터는 삭제된다")
  void blocksAfterReachingThreshold() {
    // given
    email = "block-" + UUID.randomUUID() + "@otboo.io";

    // when
    for (int i = 0; i < 5; i++) {
      loginAttemptService.recordFailure(email);
    }

    // then
    assertThat(loginAttemptService.isBlocked(email)).isTrue();
    assertThat(redisTemplate.hasKey(ATTEMPT_KEY_PREFIX + email)).isFalse();
  }

  @Test
  @DisplayName("로그인 성공 시 실패 카운트가 초기화된다")
  void recordSuccessResetsAttemptCount() {
    // given
    email = "success-" + UUID.randomUUID() + "@otboo.io";
    loginAttemptService.recordFailure(email);
    loginAttemptService.recordFailure(email);

    // when
    loginAttemptService.recordSuccess(email);

    // then
    assertThat(redisTemplate.hasKey(ATTEMPT_KEY_PREFIX + email)).isFalse();
  }

  @Test
  @DisplayName("최초 실패 시 실패 카운터에 TTL이 설정된다")
  void firstFailureSetsExpiration() {
    // given
    email = "ttl-" + UUID.randomUUID() + "@otboo.io";

    // when
    loginAttemptService.recordFailure(email);

    // then
    Long ttl = redisTemplate.getExpire(ATTEMPT_KEY_PREFIX + email, TimeUnit.SECONDS);
    assertThat(ttl).isGreaterThan(0);
  }

  @Test
  @DisplayName("동시에 여러 실패 요청이 들어와도 정확히 임계치에서만 차단된다 (Lua Script 원자성 검증)")
  void concurrentFailuresAreCountedAtomically() throws InterruptedException {
    // given
    email = "concurrent-" + UUID.randomUUID() + "@otboo.io";
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    // when: 10개의 스레드가 동시에 실패를 기록한다.
    for (int i = 0; i < threadCount; i++) {
      executor.submit(() -> loginAttemptService.recordFailure(email));
    }
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    // then: increment가 원자적이므로, 10번의 동시 요청이 정확히 카운트되어
    // 반드시 차단 상태가 되어야 한다 (경쟁 상태로 인한 카운트 유실이 없어야 함).
    assertThat(loginAttemptService.isBlocked(email)).isTrue();
  }
}