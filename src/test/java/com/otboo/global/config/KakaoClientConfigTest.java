package com.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.exception.KakaoApiException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoClientConfigTest {

  private final KakaoClientConfig kakaoClientConfig = new KakaoClientConfig();

  private ServerSocket serverSocket;
  private ExecutorService acceptExecutor;
  private final AtomicReference<Socket> acceptedSocket = new AtomicReference<>();

  @BeforeEach
  void setUp() throws IOException {
    // 연결은 받아주지만 응답은 절대 주지 않는 서버 - 응답 타임아웃(5초)을 실제로 유발하기 위함
    serverSocket = new ServerSocket(0);
    acceptExecutor = Executors.newSingleThreadExecutor();
    acceptExecutor.submit(() -> {
      try {
        acceptedSocket.set(serverSocket.accept());
      } catch (IOException ignored) {
        // 테스트 종료 시 serverSocket을 닫으면서 발생하는 예외는 무시
      }
    });
  }

  @AfterEach
  void tearDown() throws IOException {
    serverSocket.close();
    Socket socket = acceptedSocket.get();
    if (socket != null) {
      socket.close();
    }
    acceptExecutor.shutdownNow();
  }

  @Test
  @DisplayName("카카오 서버가 연결만 받고 응답이 없으면 응답 타임아웃(5초) 근방에서 KakaoApiException을 던진다")
  void throwsKakaoApiExceptionOnReadTimeout() {
    // given
    String baseUrl = "http://127.0.0.1:" + serverSocket.getLocalPort();
    KakaoLocationClient kakaoLocationClient =
        kakaoClientConfig.kakaoLocationClient(baseUrl, "test-api-key");

    // when
    long start = System.nanoTime();

    // then
    assertThatThrownBy(() -> kakaoLocationClient.getRegion(37.5665, 126.9780))
        .isInstanceOf(KakaoApiException.class);

    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
    assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(9));
  }
}