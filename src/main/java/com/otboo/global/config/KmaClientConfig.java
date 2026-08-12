package com.otboo.global.config;

import com.otboo.domain.weather.client.KmaWeatherClient;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class KmaClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
  // 요청 전체(연결~응답 다 받기)에 대한 절대 시간제한 - READ_TIMEOUT은 read 사이 간격만 보므로,
  // 응답이 끊기지 않고 계속(느리게) 오면 그것만으로는 안 걸린다.
  private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(10);

  @Bean
  public KmaWeatherClient kmaWeatherClient(
      @Value("${kma.vilage-fcst.base-url}") String baseUrl,
      @Value("${kma.vilage-fcst.api-key}") String apiKey
  ) {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
        .responseTimeout(READ_TIMEOUT);

    WebClient webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();

    return new KmaWeatherClient(webClient, apiKey, TOTAL_TIMEOUT);
  }
}