package com.otboo.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.otboo.domain.notification.sse.SseEmitterRegistry;
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

  @Test
  @DisplayName("동일 사용자 재연결 후 이전 세션을 제거해도 새 세션은 유지된다")
  void remove_oldSession_doesNotRemoveNewSession() {
    UUID userId = UUID.randomUUID();

    SseEmitter oldEmitter = new SseEmitter(1000L);
    SseEmitter newEmitter = new SseEmitter(1000L);

    SseEmitterRegistry.ClientSession oldSession =
        sseEmitterRegistry.add(userId, oldEmitter);
    SseEmitterRegistry.ClientSession newSession =
        sseEmitterRegistry.add(userId, newEmitter);

    boolean removed = sseEmitterRegistry.remove(userId, oldSession);

    assertThat(removed).isFalse();
    assertThat(sseEmitterRegistry.get(userId)).contains(newSession);
  }

  @Test
  @DisplayName("현재 등록된 세션을 조건부 제거한다")
  void remove_currentSession_success() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(1000L);

    SseEmitterRegistry.ClientSession session =
        sseEmitterRegistry.add(userId, emitter);

    boolean removed = sseEmitterRegistry.remove(userId, session);

    assertThat(removed).isTrue();
    assertThat(sseEmitterRegistry.get(userId)).isEmpty();
  }

  @Test
  @DisplayName("전체 SSE 세션 목록 조회")
  void all_success() {
    UUID firstUserId = UUID.randomUUID();
    UUID secondUserId = UUID.randomUUID();

    SseEmitterRegistry.ClientSession firstSession =
        sseEmitterRegistry.add(firstUserId, new SseEmitter(1000L));
    SseEmitterRegistry.ClientSession secondSession =
        sseEmitterRegistry.add(secondUserId, new SseEmitter(1000L));

    assertThat(sseEmitterRegistry.all())
        .containsExactlyInAnyOrder(firstSession, secondSession);
  }

  @Test
  @DisplayName("SSE 세션 touch 시 마지막 활성 시간이 갱신된다")
  void clientSession_touch_success() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter(1000L);

    SseEmitterRegistry.ClientSession session =
        sseEmitterRegistry.add(userId, emitter);

    var before = session.getLastActiveAt();

    session.touch();

    assertThat(session.getLastActiveAt()).isAfterOrEqualTo(before);
  }

  @Test
  @DisplayName("기존 SSE 세션이 교체되면 이전 emitter를 완료한다")
  void add_replacesOldSessionAndCompletesOldEmitter() {
    UUID userId = UUID.randomUUID();

    SseEmitter oldEmitter = mock(SseEmitter.class);
    SseEmitter newEmitter = mock(SseEmitter.class);

    sseEmitterRegistry.add(userId, oldEmitter);
    SseEmitterRegistry.ClientSession newSession =
        sseEmitterRegistry.add(userId, newEmitter);

    verify(oldEmitter).complete();
    assertThat(sseEmitterRegistry.get(userId)).contains(newSession);
  }

  @Test
  @DisplayName("SSE emitter 콜백을 등록한다")
  void add_registersEmitterCallbacks() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);

    sseEmitterRegistry.add(userId, emitter);

    verify(emitter).onCompletion(any(Runnable.class));
    verify(emitter).onTimeout(any(Runnable.class));
    verify(emitter).onError(any());
  }
}