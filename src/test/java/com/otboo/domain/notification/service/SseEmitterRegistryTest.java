package com.otboo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

  private final SseEmitterRegistry sseEmitterRegistry = new SseEmitterRegistry();

  @Test
  @DisplayName("SSE 연결 등록, 조회 테스트")
  void addAndGet_success() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(1000L);

    SseEmitterRegistry.ClientSession session =
        sseEmitterRegistry.add(userId, emitter);

    Optional<SseEmitterRegistry.ClientSession> result =
        sseEmitterRegistry.get(userId);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(session);
    assertThat(result.get().getUserId()).isEqualTo(userId);
    assertThat(result.get().getEmitter()).isEqualTo(emitter);
  }

  @Test
  @DisplayName("SSE 연결을 제거 테스트")
  void remove_success() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(1000L);

    sseEmitterRegistry.add(userId, emitter);
    sseEmitterRegistry.remove(userId);

    assertThat(sseEmitterRegistry.get(userId)).isEmpty();
  }
}