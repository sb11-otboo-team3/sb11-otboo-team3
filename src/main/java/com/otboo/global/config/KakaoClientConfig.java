package com.otboo.global.config;

import com.otboo.domain.weather.client.KakaoLocationClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
        .withConnectTimeout(CONNECT_TIMEOUT)
        .withReadTimeout(READ_TIMEOUT);
    ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
        .build(settings);

    return new KakaoLocationClient(
        RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build(),
        apiKey
    );
  }
}