package com.otboo.domain.notification.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.otboo.domain.notification.sse.SseEmitterRegistry.ClientSession;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseHeartbeatSchedulerTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    @Mock
    private ClientSession clientSession;

    @Mock
    private SseEmitter emitter;

    @InjectMocks
    private SseHeartbeatScheduler sseHeartbeatScheduler;

    @Test
    @DisplayName("등록된 SSE 세션에 heartbeat를 전송하고 마지막 활성 시간을 갱신한다")
    void sendHeartbeat_success() throws IOException {
        given(sseEmitterRegistry.all())
                .willReturn(List.of(clientSession));
        given(clientSession.getEmitter())
                .willReturn(emitter);

        sseHeartbeatScheduler.sendHeartbeat();

        verify(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));
        verify(clientSession)
                .touch();
        verify(sseEmitterRegistry, never())
                .remove(any(UUID.class), any(ClientSession.class));
    }

    @Test
    @DisplayName("heartbeat 전송에 실패한 SSE 세션은 Registry에서 제거한다")
    void sendHeartbeat_sendFail_removesSession() throws IOException {
        UUID userId = UUID.randomUUID();

        given(sseEmitterRegistry.all())
                .willReturn(List.of(clientSession));
        given(clientSession.getEmitter())
                .willReturn(emitter);
        given(clientSession.getUserId())
                .willReturn(userId);

        willThrow(new IOException("SSE 연결 종료"))
                .given(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        sseHeartbeatScheduler.sendHeartbeat();

        verify(sseEmitterRegistry)
                .remove(userId, clientSession);
        verify(clientSession, never())
                .touch();
    }
}