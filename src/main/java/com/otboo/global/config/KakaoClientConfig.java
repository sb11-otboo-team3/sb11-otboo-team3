package com.otboo.global.config;

import com.otboo.domain.weather.client.KakaoLocationClient;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class KakaoClientConfig {

  //커넥션 타임 아웃 3초
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  //응답 타임아웃 5초
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  public KakaoLocationClient kakaoLocationClient(
      @Value("${kakao.local.base-url}") String baseUrl,
      @Value("${kakao.local.api-key}") String apiKey
  ) {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
        .responseTimeout(READ_TIMEOUT);

    WebClient webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();

    return new KakaoLocationClient(webClient, apiKey);
  }
}