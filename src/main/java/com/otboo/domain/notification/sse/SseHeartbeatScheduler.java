package com.otboo.domain.notification.sse;

import com.otboo.domain.notification.sse.SseEmitterRegistry.ClientSession;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseEmitterRegistry sseEmitterRegistry;

    @Scheduled(
            fixedDelayString = "${app.notification.sse.heartbeat-interval-ms:20000}"
    )
    public void sendHeartbeat() {
        for (ClientSession session : sseEmitterRegistry.all()) {
            try {
                session.getEmitter().send(SseEmitter.event().comment("heartbeat"));

                session.touch();
            } catch (IOException | IllegalStateException exception) {
                log.debug(
                        "SSE heartbeat 전송 실패로 연결 제거: userId={}, error={}",
                        session.getUserId(),exception.toString()
                );

                sseEmitterRegistry.remove(session.getUserId(), session);
            }
        }
    }
}
