package com.otboo.global.config;

import com.otboo.domain.weather.client.KakaoLocationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoClientConfig {

  @Bean
  public KakaoLocationClient kakaoLocationClient(
      @Value("${kakao.local.base-url}") String baseUrl,
      @Value("${kakao.local.api-key}") String apiKey
  ) {
    return new KakaoLocationClient(RestClient.builder().baseUrl(baseUrl).build(), apiKey);
  }
}