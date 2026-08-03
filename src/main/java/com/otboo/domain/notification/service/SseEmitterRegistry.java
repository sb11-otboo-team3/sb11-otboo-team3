package com.otboo.domain.notification.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class SseEmitterRegistry {

  private final Map<UUID, ClientSession> clients = new ConcurrentHashMap<>();

  @Getter
  public static class ClientSession {

    private final UUID userId;
    private final SseEmitter emitter;
    private volatile Instant lastActiveAt;

    public ClientSession(UUID userId, SseEmitter emitter) {
      this.userId = userId;
      this.emitter = emitter;
      this.lastActiveAt = Instant.now();
    }

    public void touch() {
      this.lastActiveAt = Instant.now();
    }
  }

  public ClientSession add(UUID userId, SseEmitter emitter) {
    ClientSession session = new ClientSession(userId, emitter);
    clients.put(userId, session);

    emitter.onCompletion(() -> {
      log.info("SSE 연결 완료: userId={}", userId);
      clients.remove(userId);
    });

    emitter.onTimeout(() -> {
      log.warn("SSE 타임아웃: userId={}", userId);
      clients.remove(userId);
    });

    emitter.onError(error -> {
      log.warn("SSE 오류: userId={}, error={}", userId, error.toString());
      clients.remove(userId);
    });

    return session;
  }

  public void remove(UUID userId) {
    clients.remove(userId);
  }

  public Optional<ClientSession> get(UUID userId) {
    return Optional.ofNullable(clients.get(userId));
  }

  public Collection<ClientSession> all() {
    return clients.values();
  }
}